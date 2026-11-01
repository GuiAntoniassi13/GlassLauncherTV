# Usar como tela inicial (botão Home)

Isso muda o comportamento padrão da TV. Nada aqui exige root ou altera o firmware, e tudo é reversível. Mesmo assim, faça com calma e guarde os comandos de volta.

## Ativar

```bash
adb shell cmd role add-role-holder android.app.role.HOME com.vidro.launcher
adb shell cmd package set-home-activity com.vidro.launcher/.MainActivity
```
Teste o botão Home. Se ele ainda abrir a tela do Google, o firmware a está forçando (foi o caso da Philips 55PUG7906). Nesse caso, desative-a; ela não é apagada:
```bash
adb shell pm disable-user --user 0 com.google.android.tvlauncher
```
Se outro launcher usar um serviço de acessibilidade para capturar o Home (ex.: Projectivy), desligue esse serviço em Configurações → Acessibilidade.

### Efeitos colaterais observados
- Com a tela do Google desativada, `com.google.android.tvrecommendations` pode travar uma vez a cada boot, sem aviso na tela. Para evitar, desative-o também: `adb shell pm disable-user --user 0 com.google.android.tvrecommendations`.
- Uma atualização de sistema pode reativar a tela do Google; basta repetir os comandos.

## Desfazer (antes de desinstalar)

```bash
adb shell pm enable com.google.android.tvlauncher
adb shell pm enable com.google.android.tvrecommendations
adb shell cmd role add-role-holder android.app.role.HOME com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb uninstall com.vidro.launcher
```
Se aparecer tela preta ou o seletor de launcher, reinicie a TV (`adb reboot`). Sem ADB: Configurações → Apps → Apps do sistema → "Android TV Home" → Ativar.

## Atualizar sem perder ajustes

```bash
adb install -r GlassLauncherTV-NOVA.apk
```
O Android só aceita a atualização por cima se o APK novo tiver **a mesma assinatura** do instalado e um `versionCode` igual ou maior. Caso contrário, é preciso desinstalar, e com isso os favoritos, os ajustes e o papel de tela inicial se perdem.
