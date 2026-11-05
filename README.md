# speeduino-dash-android

**English:** a minimal, open source Android dashboard for [Speeduino](https://speeduino.com) ECUs over Bluetooth (HC-05/HC-06). It shows 8 live values (RPM, MAP, TPS, coolant and intake air temperature, battery, injector pulse width and AFR) with gauge or big-number layouts and red out-of-range alerts. It adapts itself to the firmware version by reading the matching `speeduino.ini` (bundled, cached, downloaded from the official Speeduino GitHub, or picked by the user). Android 5.0+ (API 21), plain Java, no external libraries. Contributions are welcome. Documentation below is in Portuguese.

---

Desenvolvido como parte do Trabalho de Conclusão de Curso de Osvaldo Rodrigues de Faria Junior (Sistemas de Informação, Universidade Federal de Lavras, 2026): ECU de injeção eletrônica de baixo custo e código aberto, baseada no Speeduino, para modernizar motores carburados.

App mínimo: conecta pelo Bluetooth ao módulo HC-06 da ECU (Speeduino) e mostra, ao vivo, 8 grandezas: rotação, MAP, borboleta (TPS), temperatura do motor, temperatura do ar, bateria, tempo de injeção e AFR.

- **Linguagem**: Java, sem bibliotecas externas (só a API do Android).
- **Android mínimo**: 5.0 (API 21).
- **Layouts**: relógios ou números grandes (botão "Layout"; a escolha fica salva).
- **Alertas**: fora da faixa, o mostrador fica vermelho. Hoje: bateria abaixo de 11,5 V ou acima de 14,7 V; temperatura do motor acima de 105 °C. As faixas ficam em `modelo/Grandeza.java`.
- **Não grava log** (fora do escopo do app mínimo).

## Como o app se adapta ao firmware

A ECU manda os dados ao vivo como um bloco de bytes sem rótulos. A posição de cada grandeza está no `.ini` do firmware. Ao conectar, o app:

1. pergunta a assinatura (`Q`) e a versão completa (`S`, ex.: "Speeduino 2025.01.6" → versão `202501.6`);
2. procura o `.ini` dessa versão: embutido no app (`assets/ini/`), depois no cache, depois no GitHub oficial do Speeduino (`speeduino/speeduino`, tag da versão, `reference/speeduino.ini`), e por fim pede ao usuário para escolher o arquivo;
3. lê a posição de cada grandeza **pelo nome do canal** na seção `[OutputChannels]` (`rpm`, `map`, `tps`, `coolantRaw`, `iatRaw`, `batteryVoltage`, `pulseWidth`, `afr`).

Se uma versão nova do firmware mudar um canal de lugar, o app acha a posição nova sozinho. Se um canal for renomeado, o mostrador dele exibe "--".

## Protocolo

O mesmo do TunerStudio (modo "Tuner Studio" da Serial3 da ECU), conferido em `comms.cpp` do firmware 202501.1: cada mensagem vai num envelope `[tamanho, 2 bytes big-endian][conteúdo][CRC32, 4 bytes big-endian]`. Os dados ao vivo vêm do comando `r 0x00 0x30 [posição LE][quantidade LE]`. O app e o TunerStudio não podem estar conectados ao mesmo tempo (o HC-06 aceita uma conexão só).

## Estrutura

| Pacote / classe | Papel |
|---|---|
| `MainActivity` | Tela: lista de módulos pareados, conectar, mostradores, troca de layout, permissões |
| `LeituraContinua` | Laço de leitura (~10 vezes por segundo) em thread própria |
| `bluetooth/ConexaoBluetooth` | Conexão SPP (porta serial) com o HC-06 |
| `protocolo/ClienteSpeeduino` | Envelope, CRC32 e comandos `Q`, `S` e `r` |
| `ini/DefinicaoIni`, `ini/DefinicaoCanal` | Leitura do `.ini` e conversão de cada canal |
| `ini/RepositorioIni` | Busca do `.ini` (embutido, cache, GitHub ou arquivo escolhido) |
| `modelo/Grandeza` | As 8 grandezas, escalas dos mostradores e faixas de alerta |
| `ui/Mostrador`, `MostradorRelogio`, `MostradorNumero` | Os dois layouts |

## Compilar e instalar

1. Abrir esta pasta no Android Studio (File → Open). Ele cria o `local.properties` com o caminho do SDK.
2. Celular com depuração USB ligada → botão Run.
3. Antes de abrir o app: parear o `ECU_TCC` nas configurações de Bluetooth do celular (PIN do módulo).

Versões usadas: Gradle 9.8.2, Android Gradle Plugin 9.4.1, compileSdk/targetSdk 37. Compilado sem erros em 2026-11-04.

## Contribuir

Contribuições são bem-vindas: abra uma *issue* para relatar problemas ou sugerir melhorias, ou envie um *pull request*. Ao contribuir, você concorda em licenciar a sua contribuição sob a GPL-3.0.

## Licença

Este app é distribuído sob a **GNU General Public License v3.0** (ver `LICENSE`).

O arquivo `app/src/main/assets/ini/202501.6.ini` é o `reference/speeduino.ini` do projeto [Speeduino](https://github.com/speeduino/speeduino) (tag `202501.6`), distribuído sem alterações sob a licença do Speeduino (GNU GPL versão 2 ou, a critério do usuário, qualquer versão posterior). Os direitos sobre esse arquivo são dos autores do Speeduino.

## Falta

- Testar no carro, com a ECU de verdade (até agora só compilado e testado o leitor do `.ini`).
- Testar num celular antigo (Android 5 a 11) e num novo (12 ou mais), por causa das permissões.
- Documentação de engenharia de software (requisitos, casos de uso, classes, sequência, testes) para o apêndice do TCC.
