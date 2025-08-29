package git.yannynz.organizadorproducao.controller;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;

@RestController
@RequestMapping({"/ops", "/api/ops"}) 
public class OpFileController {

  private final OpImportRepository repo;

  @Value("${app.smb.domain:}")
  private String smbDomain;
  @Value("${app.smb.username:}")
  private String smbUser;
  @Value("${app.smb.password:}")
  private String smbPass;

  public OpFileController(OpImportRepository repo) {
    this.repo = repo;
  }

  @GetMapping(value = "/{numero}/arquivo", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<StreamingResponseBody> abrir(@PathVariable String numero) {
    OpImport op = repo.findByNumeroOp(numero)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OP não encontrada"));

    String sharePath = op.getSharePath();
    if (sharePath == null || sharePath.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sharePath vazio");
    }

    // 1) caminho local visível no container
    Path local = Paths.get(sharePath);
    if (Files.exists(local) && Files.isRegularFile(local)) {
      StreamingResponseBody body = out -> Files.copy(local, out);
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + numero + ".pdf\"")
          .contentType(MediaType.APPLICATION_PDF)
          .body(body);
    }

    // 2) UNC via SMBJ
    if (sharePath.startsWith("\\\\")) {
      return streamViaSMB(sharePath, numero);
    }

    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado (local/SMB)");
  }

  private ResponseEntity<StreamingResponseBody> streamViaSMB(String unc, String numero) {
    // normaliza e extrai host/share/path
    String p = unc.replace("/", "\\").replaceFirst("^\\\\\\\\", "");
    int i = p.indexOf('\\');
    if (i < 0) throw notFound("UNC inválido");
    String host = p.substring(0, i);
    String rest = p.substring(i + 1);
    int j = rest.indexOf('\\');
    if (j < 0) throw notFound("UNC sem share");
    String share = rest.substring(0, j);
    String pathInShare = rest.substring(j + 1);

    StreamingResponseBody body = out -> {
      com.hierynomus.smbj.SMBClient client = new com.hierynomus.smbj.SMBClient();
      try (client;
           com.hierynomus.smbj.connection.Connection conn = client.connect(host)) {

        com.hierynomus.smbj.auth.AuthenticationContext ac =
            new com.hierynomus.smbj.auth.AuthenticationContext(
                smbUser != null ? smbUser : "",
                smbPass != null ? smbPass.toCharArray() : new char[0],
                (smbDomain != null && !smbDomain.isBlank()) ? smbDomain : null
            );

        com.hierynomus.smbj.session.Session session = conn.authenticate(ac);

        try (com.hierynomus.smbj.share.DiskShare disk =
                 (com.hierynomus.smbj.share.DiskShare) session.connectShare(share);
             com.hierynomus.smbj.share.File f = disk.openFile(
                 pathInShare,
                 EnumSet.of(AccessMask.GENERIC_READ),
                 EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                 EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                 SMB2CreateDisposition.FILE_OPEN,
                 null);
             InputStream in = f.getInputStream()) {
          in.transferTo(out);
        }
      }
    };

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + numero + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(body);
  }

  private static ResponseStatusException notFound(String msg) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, msg);
  }
}

