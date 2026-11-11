# BMW Digital Cockpit · Android nativo

Cockpit offline para centrais Android e celulares (Android 6 ou superior). Kotlin, Compose Canvas, Navigation Compose, ViewModel/StateFlow e Preferences DataStore. O painel inicia no simulador e identifica a origem no cabeçalho. Cada dimensão é recalculada em orientação horizontal e vertical.

## Operação

- **Painel** abre o seletor de quatro layouts quando tocado novamente. O controle no canto direito altera o ritmo, pausa e inicia a simulação. Os cartões inferiores permitem trocar o sensor.
- **Telemetria** exibe até cinco minutos de histórico. Curvas com unidades diferentes são normalizadas pela faixa de cada sensor. Gravações locais podem ser exportadas pelo seletor de documentos do Android. O CSV inclui timestamp, modo, unidade e origem de cada leitura. A gravação termina quando a Activity sai do primeiro plano.
- **Personalizar** permite adicionar instrumentos, arrastar, mudar dimensão, cor, sensor e estilo. Salvar aplica a composição ao painel. Escolher um dos quatro layouts retorna ao painel de fábrica. O editor mantém a última composição salva.
- **Configurações → Conexão OBD2 → Veículo** permite procurar e selecionar o transporte. Bluetooth Classic usa RFCOMM SPP. BLE reconhece o perfil UART FFF0 (FFF1 notificação, FFF2 escrita) e Nordic UART. Outro perfil requer identificar o modelo e as características anunciadas. No Android 6–11 a descoberta requer permissão de localização e pode depender dos serviços de localização da central.
- O leitor consulta a máscara de PIDs da ECU do motor em CAN ISO 15765, separa os endereços de resposta, aplica timeout e reconecta quando habilitado. Torque e potência são estimativas derivadas dos PIDs de torque; turbo relativo exige MAP e pressão atmosférica. A tensão é do módulo de controle. Marcha e pressão do óleo ficam indisponíveis no modo real. SPORT+ é ilustrativo no simulador.
- **Diagnóstico** lê DTCs armazenados com serviço 03 e guarda histórico local por origem. Não há operação de escrita ou limpeza da ECU. O modo real precisa de validação física no Vgate e no veículo.
- As medições de desempenho usam amostras de velocidade e tempo. Para 0–100 e ¼ de milha, arme a medição e parta da imobilidade. No simulador use “Partir do zero”. Em Voltas, o botão marca o fim da volta.

## Build e testes

Java 17, Gradle 8.10.2, Android SDK 35. Execute `gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. No CI, um emulador API 30 abre os layouts e ferramentas, exercita interações e captura as telas. Uma segunda execução, após encerrar o processo, verifica o DataStore. Um build verde disponibiliza os artefatos de validação e o APK de depuração.
