package git.yannynz.organizadorproducao.controller;

import git.yannynz.organizadorproducao.model.OpImport;
import git.yannynz.organizadorproducao.repository.OpImportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.file.*;
import java.util.EnumSet;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;

@RestController
// aceita /ops/... (oficial) e /orders/ops/... (compat temporária)
@RequestMapping({"/api/ops", "/ops", "/api/orders/ops", "/orders/ops"})
@RequiredArgsConstructor
public class OpImportController {

  private final OpImportRepository repo;

  @GetMapping(value = "/{numero}/arquivo", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<StreamingResponseBody> abrir(@PathVariable String numero) {
    OpImport op = repo.findByNumeroOp(numero)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "OP não encontrada"));

    final String sharePath = op.getSharePath();   // precisa existir o getter
    if (sharePath == null || sharePath.isBlank()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sharePath vazio");
    }

    // 1) caminho local montado no container
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
    // \\HOST\SHARE\dir\...\arquivo.pdf
    String p = unc.replace("/", "\\").replaceFirst("^\\\\\\\\",""); // tira os \\ iniciais
    int i = p.indexOf('\\'); if (i < 0) throw bad("UNC inválido");
    String host = p.substring(0, i);
    String rest = p.substring(i + 1);
    int j = rest.indexOf('\\'); if (j < 0) throw bad("UNC sem share");
    String share = rest.substring(0, j);
    String pathInShare = rest.substring(j + 1);

    StreamingResponseBody body = out -> {
      com.hierynomus.smbj.SMBClient client = new com.hierynomus.smbj.SMBClient();
      try (client;
           com.hierynomus.smbj.connection.Connection conn = client.connect(host)) {

        String domain = System.getenv().getOrDefault("APP_SMB_DOMAIN", "");
        String user   = System.getenv().getOrDefault("APP_SMB_USERNAME", "");
        String pass   = System.getenv().getOrDefault("APP_SMB_PASSWORD", "");

        com.hierynomus.smbj.session.Session session = conn.authenticate(
            new com.hierynomus.smbj.auth.AuthenticationContext(
                user, pass.toCharArray(), domain.isBlank() ? null : domain
            )
        );

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

  private static ResponseStatusException bad(String m) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, m);
  }
}

