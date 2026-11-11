# BMW Digital Cockpit v0.3

Demonstração offline para multimídias Android em orientação horizontal. Os nove painéis usam recortes sem recompressão com perdas da referência fornecida. O app preserva a proporção de cada tela com margens nas resoluções diferentes. A nitidez da arte é limitada à imagem de origem.

## Navegação

- **Painel** abre o layout selecionado. Toque novamente em Painel, ou nos mostradores, para abrir o seletor de layouts. As setas laterais trocam de painel.
- **Telemetria** abre o gráfico. O menu lateral dá acesso às medições e ao diagnóstico demonstrativos.
- **Personalizar** abre o editor. Selecione um sensor, arraste o instrumento, ajuste o tamanho e salve. Toque novamente em Personalizar para acessar Temas e Skins.
- **Configurações** abre as preferências. Exportar/Importar permite copiar e restaurar as preferências em JSON.
- **Voltar** retorna à tela anterior. No início, dois toques seguidos em Voltar encerram o app.

O estado Vgate, os dados do carro, os códigos de falha e os tempos são exemplos visuais. O app não abre uma conexão Bluetooth e não envia comandos à ECU. Se o componente WebView do Android estiver indisponível, o modo de visualização local permite percorrer as nove telas tocando na imagem.

## Compilar e visualizar

O workflow **Build APK** usa Java 17 e Gradle 8.10.2 para gerar o APK de depuração. O ZIP em **Artifacts** contém o APK. Para abrir a interface no computador, abra `app/src/main/assets/index.html` com os arquivos adjacentes.
