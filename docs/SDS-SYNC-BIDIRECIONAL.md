# Especificação Técnica: Sincronização Bidirecional de Arquivos

**Contexto:** Sincronizar alterações de prioridade entre o Sistema Web (Java) e o Sistema de Arquivos (Windows/Linux via C#).
**Projetos Envolvidos:** `organizador-producao` (Backend Java) e `FileWatcherApp` (Serviço C#).

---

# 1. Fluxo A: Arquivo → Sistema (File Watcher → Java)

Este fluxo ocorre quando um operador vai na pasta `/laser` e renomeia `NR123_VERMELHO.dxf` para `NR123_AZUL.dxf`.

### 1.1. Análise do Estado Atual
*   **C# (`FileWatcherService.cs`):** O evento `Renamed` já é capturado. O sistema normaliza o evento e o envia para a fila `laser_notifications` como se fosse um novo arquivo. **Nenhuma alteração de código necessária aqui.**
*   **Java (`FileWatcherService.java`):** Ao receber a mensagem, o sistema extrai o NR. Se o NR já existe no banco, ele ignora a mensagem.

### 1.2. Alterações Necessárias (Backend Java)

**Arquivo Alvo:** `src/main/java/git/yannynz/organizadorproducao/service/FileWatcherService.java`

**Lógica a Implementar (`processFile`):**

```java
// Pseudocódigo da nova lógica
Optional<Order> existingOrder = orderRepository.findByNr(orderNumber);

if (existingOrder.isPresent()) {
    Order order = existingOrder.get();
    
    // Verifica se a prioridade mudou (ex: de VERMELHO para AZUL)
    if (!order.getPrioridade().equalsIgnoreCase(priority)) {
        String oldPriority = order.getPrioridade();
        order.setPrioridade(priority); // Atualiza
        orderRepository.save(order);
        
        // Notifica o Frontend via WebSocket para atualizar a cor do card em tempo real
        messagingTemplate.convertAndSend("/topic/orders", order);
        
        System.out.println("Prioridade atualizada via Arquivo: " + orderNumber + " (" + oldPriority + " -> " + priority + ")");
    } else {
        System.out.println("Arquivo renomeado/reprocessado, mas prioridade mantém-se " + priority + ". Nenhuma ação.");
    }
    return; // Sai, pois não precisa criar novo pedido
}
// ... (resto do código de criação de novo pedido)
```

---

# 2. Fluxo B: Sistema → Arquivo (Java → File Watcher)

Este fluxo ocorre quando o usuário altera a prioridade num dropdown no Frontend Angular.

### 2.1. Arquitetura da Solução
Precisamos de um canal de comando. O Java será o *Producer* e o C# será o *Consumer*.

*   **Nova Fila RabbitMQ:** `file_commands`
*   **Payload do Comando:**
    ```json
    {
      "action": "RENAME_PRIORITY",
      "nr": "12345",
      "newPriority": "AZUL", // Sufixo alvo
      "directory": "LASER" // Qual pasta buscar? (Laser ou FacasOk)
    }
    ```

### 2.2. Alterações no Backend Java

1.  **Controller (`OrderController.java`):**
    *   Criar endpoint `PATCH /api/orders/{id}/priority`.
    *   Recebe a nova prioridade.
    *   Atualiza o banco de dados (SSOT - Single Source of Truth).
    *   Dispara o evento para o RabbitMQ na fila `file_commands`.

2.  **Serviço de Publicação:**
    *   Utilizar `RabbitTemplate` para enviar o JSON acima.

### 2.3. Alterações no FileWatcherApp (C#)

Este é o trabalho mais pesado. Precisamos criar um novo Worker.

**Novo Arquivo:** `Messaging/FileCommandConsumer.cs`

**Estrutura da Classe:**

```csharp
public class FileCommandConsumer : BackgroundService
{
    private readonly string _laserPath; // Ler do IOptions<FileWatcherOptions>
    
    // ... Construtor injetando Logger, RabbitMqConnection, Options ...

    protected override Task ExecuteAsync(CancellationToken stoppingToken)
    {
        // Configurar Consumidor RabbitMQ na fila "file_commands"
        // ...
        consumer.Received += HandleCommandAsync;
    }

    private async Task HandleCommandAsync(object sender, BasicDeliverEventArgs ea)
    {
        // 1. Deserializar JSON
        var command = JsonSerializer.Deserialize<FileCommandDto>(...);
        
        if (command.Action == "RENAME_PRIORITY") 
        {
            // 2. Encontrar o arquivo
            // Busca arquivos que comecem com "NR{nr}" na pasta _laserPath
            var files = Directory.GetFiles(_laserPath, $"NR{command.Nr}*_*.dxf");
            
            foreach (var oldPath in files)
            {
                // 3. Calcular novo nome
                // Substitui _VERMELHO, _AMARELO, etc pelo novo sufixo
                string folder = Path.GetDirectoryName(oldPath);
                string filename = Path.GetFileNameWithoutExtension(oldPath);
                
                // Regex para trocar apenas o sufixo de cor final
                string newName = Regex.Replace(filename, 
                    @"_(VERMELHO|AMARELO|AZUL|VERDE)$", 
                    $"_{command.NewPriority}", 
                    RegexOptions.IgnoreCase);
                
                string newPath = Path.Combine(folder, newName + ".dxf");

                // 4. Executar Rename com Retry (Polly ou loop simples)
                try {
                    File.Move(oldPath, newPath);
                    _logger.LogInformation("Renomeado via Comando: {Old} -> {New}", oldPath, newPath);
                } catch (IOException) {
                    // Arquivo em uso pelo Laser? Rejeitar mensagem (Nack) para tentar depois?
                    // Ou Logar erro e avisar o Java (via outra fila)?
                    // MVP: Logar erro.
                }
            }
        }
        
        // Ack da mensagem
        _channel.BasicAck(ea.DeliveryTag, false);
    }
}
```

**Registro no `Program.cs`:**
```csharp
services.AddHostedService<FileCommandConsumer>();
```

---

# 3. Tratamento de Concorrência e "Loop Infinito"

**O Problema:**
1. Java manda comando "Mudar para AZUL".
2. C# renomeia arquivo para AZUL.
3. C# detecta evento `Renamed` no sistema de arquivos.
4. C# manda notificação para o Java "Arquivo mudou".
5. Java recebe notificação.

**A Solução (Idempotência):**
A lógica que desenhamos no Passo 1.2 já resolve isso.
*   Quando o Java receber a notificação do passo 4, ele vai comparar: `Prioridade do Banco (AZUL) == Prioridade do Arquivo (AZUL)?`.
*   A resposta será **SIM**.
*   O Java faz o "early return" e não faz nada. **Ciclo quebrado com sucesso.**

---

# 4. Roadmap de Desenvolvimento

1.  **Passo 1 (Seguro):** Implementar a lógica no Java (`FileWatcherService`) para aceitar atualizações vindas da pasta.
    *   *Ganho:* Usuários podem organizar a fila mexendo nos arquivos.
    *   *Risco:* Zero.
2.  **Passo 2 (Infra):** Criar a fila `file_commands` no RabbitMQ.
3.  **Passo 3 (C#):** Implementar `FileCommandConsumer` no `FileWatcherApp`.
4.  **Passo 4 (Frontend):** Habilitar o dropdown de prioridade na tela para chamar a API.

