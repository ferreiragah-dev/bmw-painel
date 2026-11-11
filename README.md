# BMW Digital Cockpit — Protótipo Android

Versão 0.1: demonstração visual offline, com duas opções de tema, páginas de sensores e seleção de widgets. **Todos os valores são simulados.** Não conecta ao Vgate e não lê a ECU nesta versão. As preferências ainda não persistem após fechar o aplicativo.

## Compilar sem Android Studio
1. Crie um repositório GitHub e envie o conteúdo desta pasta, incluindo `.github/workflows/android.yml`.
2. Abra **Actions > Build APK > Run workflow** (ou aguarde o build automático do push).
3. Ao terminar, baixe o artefato **BMW-Digital-Cockpit-APK** na execução do workflow e extraia `app-debug.apk`.
4. Instale o APK na multimídia Android. Pode ser necessário permitir instalação de aplicativos de fontes externas.

**Compatibilidade:** Android 6+ (API 23); layout responsivo horizontal/vertical. A compatibilidade real com a central Android específica precisa ser testada.

## Próxima fase
Conexão Bluetooth Classic/BLE conforme modelo exato do Vgate; descoberta de PIDs BMW; leitura OBD2 real; persistência de layouts; alertas. Não confunda dados simulados com medições do motor.
