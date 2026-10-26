# Faltas de conteúdo (13 out 2026)

Estes níveis ainda não têm certificado: o solucionador de beam search não os fechou dentro
do orçamento de tempo desta sessão (tentativas rápidas e profundas, com sementes e famílias
rotativas). O resto do conteúdo (533 níveis, 1599 variantes) está certificado e verificado.

## Ordinais em falta

151, 159, 170, 178, 186, 197, 202, 210, 216, 221, 229, 237, 240, 248, 249, 253, 264, 266, 267, 272, 280, 288, 291, 299, 304, 315, 317, 323, 331, 334, 350, 358, 359, 366, 374, 385, 409, 417, 425, 428, 436, 444, 446, 452, 460, 468, 479, 487, 495, 497, 503, 509, 511, 517, 522, 530, 538, 548, 554, 559, 562, 567, 573, 581, 589, 590, 599

## Como completar

```bash
export JAVA_HOME=/opt/android-studio/jbr ANDROID_HOME=/opt/android-sdk
./gradlew :leveltool:installDist
bash tools/fill_deep.sh    # repete sementes e famílias; deixa correr o tempo que for preciso
bash tools/recertify_all.sh # só se quiseres recalibrar tudo
./gradlew :leveltool:run --args='report --output report.md'
```
