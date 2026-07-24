# 🛡️ Flash SMS Blocker

App Android que **bloqueia SMS Flash (Class 0)** — aquelas mensagens que aparecem
de repente na **tela inteira**, muito usadas em **spam e phishing**. Sem root.

O app fecha automaticamente o pop-up do Flash SMS no instante em que ele aparece.

## O problema

SMS Class 0 ("Flash SMS") são mensagens que o próprio sistema Android exibe
imediatamente em cima de tudo, em vez de guardá-las na caixa de entrada (padrão
GSM 3GPP TS 23.040, campo `TP-DCS` com classe = 0). Operadoras usam para avisos,
mas golpistas abusam disso — ex.: *"sua linha será desativada, recarregue em
[link falso]"*.

## Como funciona

Em um celular **sem root**, nenhum app consegue *impedir* o pop-up de aparecer —
quem o desenha é o sistema, antes de qualquer app ser avisado. A única solução
possível é **fechá-lo instantaneamente**, e é isso que este app faz:

- Um **AccessibilityService** detecta quando a tela de Class 0 aparece e a fecha
  na hora (o pop-up pisca por uma fração de segundo e some).
- A detecção é pelo **tipo da janela**, não pelo conteúdo — não depende das
  palavras da mensagem, então é à prova de disfarce.
- **Não lê, não envia e não armazena nenhum SMS.** Funciona 100% offline, sem
  anúncios. A única permissão usada é a de **Acessibilidade**.

## ⚠️ Compatibilidade

Feito e testado no **Samsung Galaxy A73 5G (One UI / Android 13)**. A detecção
está afinada para a tela de Class 0 do Samsung:

```
com.samsung.android.messaging / ...ui.view.classzero.ClassZeroActivity
```

Em aparelhos de **outros fabricantes** (ou versões diferentes do One UI) pode não
funcionar sem reajuste. Veja **[Adaptar para outro aparelho](#adaptar-para-outro-aparelho)**.

## 📥 Instalação

1. Baixe o APK mais recente em **[Releases](../../releases)**.
2. Instale (o Android vai pedir para permitir **instalar de fontes desconhecidas**).
3. **Ative a acessibilidade** pela própria tela do sistema (assim fica permanente
   e sobrevive a reinicializações):
   *Configurações → Acessibilidade → Apps instalados → Flash SMS Blocker → **ligar***
   (em inglês: *Settings → Accessibility → Installed apps*).
4. Confira no app: o status deve ficar verde **"Proteção ativa"**. Pronto! 🎉

> **Se aparecer o aviso "Restricted setting"** (Samsung/Android 13+ bloqueia a
> acessibilidade de apps instalados fora da loja): vá em *Configurações → Apps →
> Flash SMS Blocker → ⋮ → **Permitir configurações restritas** ("Allow restricted
> settings")* e repita o passo 3.

### ⚡ Importante no Samsung: não deixe o app "dormir"

O One UI adormece apps em segundo plano para poupar bateria — e isso pode fazer o
app perder um Flash SMS. Garanta que ele fique sempre pronto:

*Configurações → Bateria → Limites de uso em segundo plano* → confira que o
**Flash SMS Blocker** **não** está em **"Apps em suspensão"** ("Sleeping apps") nem
em **"Apps em suspensão profunda"** ("Deep sleeping apps") — ou adicione-o em
**"Apps que nunca entram em suspensão"** ("Never sleeping apps").

O app é leve (funciona por eventos, sem internet, sem notificação fixa) — o
consumo de bateria é desprezível.

### 🏦 Apps de banco (Nubank etc.)

Apps bancários bloqueiam o próprio uso quando detectam **qualquer serviço de
acessibilidade desconhecido** ativo — é a defesa deles contra trojans de Pix, e
eles reagem à *capacidade* do serviço, não ao que ele faz. Se o seu banco
reclamar do Flash SMS Blocker:

1. No app, abra **⋮ → Pausar bloqueio (apps de banco)** — o serviço de
   acessibilidade é **desativado de verdade** (não é só "ignorar eventos";
   é o que o antifraude verifica).
2. Use o app do banco normalmente.
3. Depois, toque no card **"⏸ Bloqueio pausado"** e reative o serviço na tela
   de acessibilidade (o Android não permite que um app se reative sozinho).

## 🔒 Privacidade

Não coleta, não transmite e não armazena nenhum dado pessoal ou mensagem. Todo o
processamento é local. Sem rastreadores, sem anúncios, sem internet.

## Adaptar para outro aparelho

A detecção de cada aparelho fica numa lista de regras em **`FlashPopupRule.KNOWN`**
(`app/src/main/java/com/flashblocker/FlashPopupRule.java`). Suportar um aparelho
novo é, na maioria dos casos, **adicionar uma linha** lá.

**1. Descubra a identidade do pop-up — sem recompilar:**

- No app, abra **⋮ → Modo aprendizado** (liga). Nesse modo o app **não fecha nada**
  — só registra cada janela nova no histórico (e no logcat):
  ```
  adb logcat -s FlashBlocker/LEARN
  ```
- Receba um Flash SMS e procure o bloco `==== NEW WINDOW ====` com o `package`, a
  `class` e os `buttons` do pop-up. (Sem PC, dá pra ler direto no histórico do app.)
- **Desligue** o Modo aprendizado depois.

**2. Adicione a regra** em `FlashPopupRule.KNOWN`:

```java
new FlashPopupRule(
    "Nome do aparelho/ROM",
    "com.pacote.do.app.de.mensagens",  // package capturado
    "ClassZeroActivity",                // trecho da class capturada
    "class 0",                          // marcador no texto (ou null)
    new String[] { "cancel", "cancelar", "dismiss" })  // botão de descartar
```

Recompile e pronto. Se o aparelho precisar de lógica de detecção diferente, dá
para estender `FlashPopupRule` e sobrescrever `matches(...)`.

**Pull requests com o mapeamento de outros aparelhos são muito bem-vindos!** 🙌

## 🔧 Compilar

Projeto em Java puro, sem dependências externas. SDK 34, minSdk 24, JDK 17.

**Android Studio:** *File → Open* na pasta do projeto e *Build → Build APK(s)*.

**Linha de comando (Gradle wrapper):** precisa só de JDK 17 e do Android SDK
(aponte via `ANDROID_HOME` ou crie um `local.properties` com `sdk.dir=...`):

```bash
./gradlew assembleDebug
# saída: app/build/outputs/apk/debug/app-debug.apk
```

**Testes de unidade** (JUnit, rodam na JVM, sem aparelho) — a cobertura das classes
de lógica (`FlashPopupRule` e `BlockStats`) é exigida em **100%** de linhas e branches;
o build falha abaixo disso:

```bash
./gradlew testDebugUnitTest jacocoCoverageVerification
# relatório de cobertura (HTML):
./gradlew jacocoTestReport   # app/build/reports/jacoco/jacocoTestReport/html/index.html
```

**Alternativa sem Gradle** — build leve via aapt/d8, precisa do Android SDK
(platform `android-34` + build-tools) e JDK 17. São **um script por etapa**
(todos na raiz do projeto; `common.sh` guarda o que é compartilhado):

```bash
export ANDROID_HOME=/caminho/para/Android/Sdk
./build.sh            # compila → build/app-aligned.apk (NÃO assinado)
./sign.sh             # assina com chave debug → build/flash-sms-blocker.apk
./sign.sh release     # ou assina com a chave durável (pede a senha no terminal)
./install.sh          # instala no celular (auto: Wi-Fi se houver, senão USB)
./install.sh usb      # só USB  |  ./install.sh wifi  # só Wi-Fi
```

E o **`deploy.sh`** roda a esteira inteira (build → sign → install) num comando:

```bash
./deploy.sh                   # debug + dispositivo automático
./deploy.sh release wifi      # release assinado, instalado pelo Wi-Fi
```

O `install.sh` também **reativa o serviço de acessibilidade** (que o Android
desliga em todo update) e confere que ele voltou.

### Requisitos do build sem Gradle

Os scripts buildam **sem Gradle e sem internet** — mas exigem o SDK já instalado:

| Requisito | Para quê |
|---|---|
| **JDK 17** | `javac` e `keytool` |
| **Android SDK — platform `android-34`** | `android.jar` (compilar contra a API 34) |
| **Android SDK — `build-tools`** (recente) | `aapt`, `d8`, `zipalign`, `apksigner` |
| **bash** | rodar o script (Linux/Mac; no Windows via WSL ou Git Bash) |

Não precisa de Gradle, Android Studio nem internet (uma vez que o SDK esteja instalado).

### Instalando as dependências do build sem Gradle

Se você já usa o **Android Studio**, tudo isso já vem instalado (via SDK Manager) —
basta apontar o `ANDROID_HOME` para o SDK. Sem o Android Studio, instale pela linha
de comando:

**1. JDK 17**

```bash
# Debian/Ubuntu
sudo apt install openjdk-17-jdk
# Arch/Manjaro
sudo pacman -S jdk17-openjdk
# macOS (Homebrew)
brew install openjdk@17
# ou, multiplataforma, via SDKMAN (https://sdkman.io):
sdk install java 17.0.11-tem
```

**2. Android SDK (command-line tools, sem Android Studio)**

1. Baixe o *"Command line tools"* em
   <https://developer.android.com/studio#command-tools>.
2. Organize em `$ANDROID_HOME/cmdline-tools/latest/`:
   ```bash
   export ANDROID_HOME="$HOME/Android/Sdk"
   mkdir -p "$ANDROID_HOME/cmdline-tools"
   unzip commandlinetools-*.zip -d "$ANDROID_HOME/cmdline-tools"
   mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
   ```
3. Instale os componentes e aceite as licenças:
   ```bash
   sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
   sdkmanager --licenses
   ```

Adicione ao seu `~/.bashrc`/`~/.zshrc` para não repetir:
`export ANDROID_HOME="$HOME/Android/Sdk"`. Feito isso, os scripts funcionam.

### 🔑 Assinando o release

Os dois builds acima assinam com um **keystore de debug descartável** — servem para
testar, mas o Android só aceita **atualizar** um app instalado se o APK novo tiver a
**mesma assinatura** do anterior (`INSTALL_FAILED_UPDATE_INCOMPATIBLE` caso contrário).
Por isso o APK publicado em Releases é assinado com uma **chave durável**, guardada
**fora do repositório** e protegida por senha — a senha não está escrita em lugar
nenhum (nem deve estar: quem a tivesse poderia assinar uma "atualização" maliciosa).

**Criar a chave (uma única vez):**

```bash
mkdir -p ~/.keystores
keytool -genkeypair -keyalg RSA -keysize 2048 -validity 10000 \
  -keystore ~/.keystores/flash-sms-blocker.jks \
  -alias flash -dname "CN=Flash SMS Blocker"
# ele pergunta a senha; guarde-a num gerenciador de senhas — não há como recuperá-la
```

**Release sem Gradle** — o `sign.sh release` assina com a chave durável; o
`apksigner` pergunta a senha no terminal (ela não passa por variável de ambiente
nem fica no histórico do shell):

```bash
./deploy.sh release wifi    # compila, assina, instala e reativa a acessibilidade
# ou por etapas: ./build.sh && ./sign.sh release && ./install.sh wifi
```

O caminho do keystore é `~/.keystores/flash-sms-blocker.jks` por padrão (mude com
`RELEASE_KEYSTORE=...`); se ele tiver mais de um alias, defina `RELEASE_KEY_ALIAS`.

**Build release com o Gradle** — o `assembleRelease` assina sozinho se encontrar o
keystore e a senha nas variáveis de ambiente (sem elas, gera APK não assinado —
contribuidores não precisam configurar nada):

```bash
export RELEASE_KEYSTORE_PASSWORD='...'   # e opcionalmente RELEASE_KEYSTORE,
./gradlew assembleRelease                # RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
# saída: app/build/outputs/apk/release/app-release.apk
```

Notas:

- Um build de debug **não instala por cima** de um release (assinaturas diferentes):
  ou assine o build com a chave release, ou desinstale antes — desinstalar apaga as
  estatísticas e desativa o serviço de acessibilidade, que precisa ser reativado.
- **Nunca** comite o `.jks` nem a senha no repositório.

## 📲 Enviar o APK para o celular (adb)

Pré-requisito: celular em **modo desenvolvedor** com a **depuração USB** ligada
(*Configurações → Opções do desenvolvedor*).

**Jeito recomendado** — o `install.sh` instala e reativa o serviço de
acessibilidade num comando só:

```bash
./install.sh              # automático: Wi-Fi se houver, senão USB
./install.sh usb          # só USB
./install.sh wifi         # só Wi-Fi (ativa a partir do USB ou de PHONE_IP=<ip>)
```

Ele instala o `build/flash-sms-blocker.apk` **já assinado** (a assinatura é
preservada — pode ter sido assinado em outro terminal). Para compilar, assinar
e instalar tudo de uma vez: `./deploy.sh release wifi`.

**Na mão** (ex.: APK buildado pelo Gradle):

```bash
adb devices                              # o aparelho deve aparecer como "device"
adb install -r build/flash-sms-blocker.apk   # (ou app/build/outputs/apk/... no Gradle)
```

Gotchas (todos vistos na prática no A73):

- **Samsung bloqueia sideload por adb** (`INSTALL_FAILED_VERIFICATION_FAILURE`):
  desligue o **Auto Blocker** (*Configurações → Segurança e privacidade*) e o
  **Verificar apps via USB** (*Opções do desenvolvedor*).
- O celular em modo **tethering USB** some do adb — deixe o USB em
  **transferência de arquivos**.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: o APK instalado foi assinado com
  outra chave (ex.: havia um build debug e você está enviando um release).
  Não tem contorno — desinstale e instale de novo (isso zera as estatísticas
  e desativa o serviço de acessibilidade):

  ```bash
  adb uninstall com.flashblocker
  adb install build/flash-sms-blocker.apk
  ```

- **Atualizar o app desativa o serviço de acessibilidade** (comportamento do
  Android) — depois de todo `adb install -r` manual, confira o card de status no
  app ou reative pelo adb (comandos abaixo). O `./install.sh` já faz isso
  sozinho.
- **adb pelo Wi-Fi** (sem cabo): plugue o USB **uma vez** e rode:

  ```bash
  adb tcpip 5555                      # liga o modo TCP (dura até reiniciar o celular)
  adb shell ip addr show wlan0        # anote o IP do celular (linha "inet ...")
  adb connect IP_DO_CELULAR:5555      # agora pode desplugar o USB
  ```

  Nas próximas vezes (mesmo boot), basta o `adb connect`. Com o celular em duas
  conexões ao mesmo tempo (USB + Wi-Fi), direcione os comandos com
  `adb -s IP:5555 ...`.

- Para **reativar o serviço de acessibilidade sem mexer no aparelho**:

  ```bash
  adb shell settings put secure enabled_accessibility_services \
    com.flashblocker/com.flashblocker.FlashSmsAccessibilityService
  adb shell settings put secure accessibility_enabled 1
  # confirmar:
  adb shell dumpsys accessibility | grep 'Flash SMS Blocker'
  ```

## ⚖️ Aviso

Software fornecido "como está", **sem garantias** (veja a licença). Use por sua
conta e risco. Este projeto **não é afiliado** à Samsung, Google, Vivo ou a
qualquer operadora ou empresa; nomes de terceiros aparecem apenas como exemplo.

## Licença

[MIT](LICENSE) © 2026 Rafael (rdmsilva)
