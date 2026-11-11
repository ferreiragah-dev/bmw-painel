# BMW Digital Cockpit v0.2 — Visual Demo

Projeto Android com interface WebView offline, em tela horizontal, visual M Performance e Race Dashboard, telemetria simulada, temas e seleção persistente de indicadores.

**IMPORTANTE:** sem integração OBD2/Bluetooth, sem dados reais, sem leitura de falhas. Os números são demonstrativos e não devem ser usados para condução.

## Compilação no GitHub

Coloque **o conteúdo desta pasta na raiz** de um repositório GitHub (main). Em Actions > Build APK > Run workflow. O arquivo APK estará em Artifacts ao final da execução. O workflow usa Gradle 8.10.2 sem exigir gradlew.

## Visualização sem instalar

Abra `app/src/main/assets/index.html` em um navegador para pré-visualizar a interface. O APK carrega esse mesmo HTML localmente.
