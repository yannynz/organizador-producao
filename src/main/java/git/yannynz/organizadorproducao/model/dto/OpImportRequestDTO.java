package git.yannynz.organizadorproducao.model.dto; 

public record OpImportRequestDTO(
  String numeroOp,
  String codigoProduto,
  String descricaoProduto,
  String cliente,
  String dataOp,                 
  java.util.List<String> materiais,
  Boolean emborrachada,
  String sharePath              
) {}

