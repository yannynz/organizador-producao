package git.yannynz.organizadorproducao.model;

import java.util.ArrayList;
import java.util.List;

public class ErroredFiles {
    private final List<String> errorNames = new ArrayList<>();

    // Adiciona um nome de arquivo com erro à lista
    public void addErrorName(String fileName) {
        errorNames.add(fileName);
    }

    // Retorna a lista de nomes de arquivos com erro
    public List<String> getErrorNames() {
        return errorNames;
    }
}
