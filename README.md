# BMW Digital Cockpit

Aplicativo Android nativo de instrumentação e telemetria para a multimídia de uma **BMW 320i F30 2015 ActiveFlex
Stage 2**, com leitura OBD2 via adaptador **Vgate (ELM327)** e modo demonstração com simulador físico.

Todos os instrumentos — velocímetro, conta-giros, arcos, ponteiros, números, cartões, gráficos e a representação
do F30 — são desenhados programaticamente com Jetpack Compose/Canvas e respondem aos dados. O APK não contém
capturas, mockups ou WebView.

## Telas

| Área | Telas |
| --- | --- |
| **Painel** | M Performance · Race Dashboard · GT Telemetry · Minimal · Meu Painel (editor). Toque de novo em *Painel* (ou nas setas laterais) para trocar de layout. |
| **Telemetria** | Gráficos · Logs (sessões/CSV) · 0–100 km/h · 1/4 de milha · Voltas · Diagnóstico (DTC) |
| **Personalizar** | Editor de painel · Multitelas (seletor com miniaturas reais) · Temas e skins |
| **Configurações** | Conexão OBD2 · Geral · Unidades · Alarmes · Exportar/Importar · Sobre |

O indicador **MODO DEMONSTRAÇÃO** fica sempre visível no cabeçalho quando a fonte é o simulador; tocar nele abre
os controles (iniciar/pausar, reiniciar, partir da imobilidade, ritmo 0,5×–4×).

## Como usar

1. Instale o APK (Actions → *BMW-Digital-Cockpit-APK*). O app abre em modo demonstração.
2. Para o carro: pareie o Vgate no Android (modelos Bluetooth Classic, PIN 1234/0000) ou apenas ligue-o (BLE),
   abra **Configurações → Conexão OBD2 → Buscar dispositivos** e toque no adaptador. Ligue a ignição.
3. Toque em um cartão do painel para escolher o sensor exibido. Toque em *SPORT+* para trocar o perfil visual.
4. **Telemetria → Gráficos**: escolha sensores, janela (30 s–10 min), *Pausar gráfico* (congela só a visualização;
   arraste para navegar no histórico), *Iniciar sessão* (grava CSV) e *Exportar CSV* (seletor de documentos).
5. **0–100 / 1/4 de milha**: pare o carro, toque em *Armar* e acelere. No demo use *Simular arrancada*.
   **Voltas**: *Iniciar* na linha, *Marcar volta* a cada passagem, *Parar* no fim.
6. **Personalizar → Editor**: toque num sensor da biblioteca para adicionar; arraste para mover; alça azul para
   redimensionar; troque sensor, cor e estilo (circular, digital, barra); *Salvar*, *Cancelar*, *Restaurar padrão*,
   *Salvar como…* e *Carregar*; *Salvar e usar no painel* ativa o layout "Meu Painel".

## Dados e honestidade das leituras

Cada leitura tem sensor, valor, unidade, timestamp, disponibilidade e origem (`VEHICLE`, `DERIVED`, `SIMULATED`,
`UNAVAILABLE`). Indisponível ou vencida → “—”. Trocar entre veículo e demonstração limpa valores e histórico.

* **OBD2 genérico** (modo 01): somente PIDs reportados como suportados pela ECU selecionada.
* **Marcha real, pressão do óleo e dados BMW específicos** não existem no OBD2 genérico → “—” no modo veículo.
  No demo são simulados e marcados `SIM`.
* **Turbo relativo** = MAP (PID 0B) − pressão atmosférica medida (PID 33, ou MAP lido com motor parado na
  conexão). Sem referência medida o turbo fica indisponível. O PID 0B satura em 255 kPa.
* **Potência/torque (estimados)**: `combustível = MAF/(14,7·λ)`, `P[kW] = combustível·3600/330 g/kWh`,
  `T[Nm] = P·9549/RPM`. λ do PID 44 quando suportado; senão 0,85 com carga ≥ 80 % e 1,0 abaixo. Exige MAF (PID 10)
  e RPM; sem eles fica indisponível. Não é medição de dinamômetro.
* **Tensão ECU** (PID 42) = alimentação do módulo de controle; **Tensão OBD** (ATRV) = medida pelo adaptador no pino 16.
* **Diagnóstico** somente leitura (serviços 03/07/0A). Resposta incompleta, timeout ou NO DATA nunca é mostrada
  como “sem códigos”. Limpeza de códigos (serviço 04) não está implementada nem exposta.

## Bluetooth / ELM327

* **Classic**: RFCOMM/SPP (UUID 1101) com fallback inseguro e canal 1.
* **BLE**: perfis FFF0/FFF2/FFF1, FFE0/FFE1, 18F0/2AF1/2AF0 e E781…/BEF8…; serviço desconhecido com escrita +
  notificação é tentado heuristicamente; sem isso, mensagem com os UUIDs encontrados.
* Inicialização: `ATZ, ATE0, ATL0, ATS0, ATH1, ATAT1, ATSP0, 0100, ATDPN/ATDP`, seleção da ECU do motor
  (responde 0C/0D; empate → menor endereço, 7E8), filtro `ATSH/ATCRA`, descoberta 0120/0140/….
* Parser trata eco, `SEARCHING...`, respostas parciais (BLE 20 bytes), NO DATA, erros de barramento, várias ECUs
  (endereços nunca misturados) e ISO-TP multi-quadro.
* Polling sequencial (um comando por vez): RPM/velocidade a cada ciclo; MAP/acelerador 200 ms; MAF 300 ms;
  carga 400 ms; λ 1 s; temperaturas 2 s; tensão 3–5 s; baro 30 s. Modo economia reduz as taxas.
* Timeout de conexão/inicialização, 4 falhas seguidas = link perdido, reconexão automática configurável (até 5
  tentativas com espera crescente). Desconectar/trocar de fonte fecha socket/GATT/notificações e cancela tarefas.

> A integração foi testada com transporte falso e respostas OBD2 gravadas. **A compatibilidade física com o Vgate
> ainda não foi validada em hardware** (o modelo exato também precisa ser confirmado).

## Segundo plano

Sem gravação ativa, simulador e polling pausam quando o app sai da tela (Bluetooth continua conectado). Com uma
sessão sendo gravada, coleta e gravação continuam sob um *foreground service* (`dataSync`) com notificação e ação
“Parar gravação”. Sessões são gravadas linha a linha (`.csv.part`, flush a cada 1 s) e recuperadas sem corrupção se
o processo morrer.

## Arquitetura

```
data/model        Reading, SensorId, DataOrigin, TelemetrySnapshot
data/telemetry    TelemetryHub (fonte única), HistoryStore (ring buffer 3000 pts/sensor), estimadores
sim               VehicleSimulator (física F30/N20/8HP) + DemoController
obd               ElmSession, Elm327Initializer, ObdFrameParser (ECUs/ISO-TP), PidDecoder, DTC, ObdPoller
transport         ObdTransport, ClassicSppTransport, BleTransport, FakeTransport, BluetoothScanner
connection        VehicleConnectionManager (estado, timeout, reconexão, leitura de DTC)
prefs             AppSettings (DataStore + JSON tolerante), EditorLayout normalizado
session           SessionRecorder + CSV;  perf: PerformanceMeter/LapTimer;  diag: DiagnosticsController
service           RecordingService (foreground durante gravação)
ui/…              Compose: cabeçalho, gauges (Canvas), dashboards, telemetria, editor, temas, configurações
```

MVVM com `ViewModel` + `StateFlow`; o `AppContainer` (escopo da aplicação) mantém hub, simulador, conexão e
gravador, sobrevivendo a recriação da Activity e rotação.

## Compilar

Requisitos: **JDK 17**, Android SDK (platform 35, build-tools 35). Gradle 8.10.2 via wrapper, AGP 8.7.3,
Kotlin 2.0.21, Compose BOM 2024.12.01. **minSdk 24 (Android 7.0)**, targetSdk 35.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

### Screenshots no emulador

```bash
./gradlew assembleDebug assembleDebugAndroidTest
bash tools/capture-review.sh        # com um emulador/dispositivo conectado
```

O teste instrumentado `ScreenshotTour` percorre todas as telas, verifica atualização dos instrumentos, arraste e
redimensionamento no editor e o botão Voltar, gerando PNGs em `review/ci/` (fora dos recursos do app), nas
resoluções 1280×720, 1024×600, 800×480, 1280×480, 1920×720 e retrato.

## Assinatura

Builds de depuração são assinados com `keystore/cockpit-debug.jks` (chave pública, somente debug), igual em todos
os builds locais e de CI — instalações futuras podem ser atualizadas sem desinstalar. Para uma chave própria,
defina os secrets `COCKPIT_KEYSTORE_BASE64`, `COCKPIT_KEYSTORE_PASSWORD`, `COCKPIT_KEY_ALIAS` e
`COCKPIT_KEY_PASSWORD`.

**Atenção:** APKs anteriores (v0.3, WebView) foram assinados com a chave de debug aleatória do runner do GitHub;
desinstale a versão antiga uma vez antes de instalar esta. Trocar depois para uma chave própria também exige
desinstalar.

## CI

`.github/workflows/build.yml`: testes unitários, lint, APK, verificação de que o APK não contém mockups, relatórios
como Artifacts e um job com emulador (KVM) que executa o `ScreenshotTour` e publica as capturas.
