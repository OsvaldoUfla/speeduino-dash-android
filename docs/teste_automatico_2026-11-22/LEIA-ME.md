# Teste automático do app pelo ADB (22/11/2026, 21:26–21:29)

Roteiro executado pelo computador via ADB: `uiautomator dump` para achar os botões pelo texto, `input tap` para tocar e `screencap` para os prints. Celular: moto g31 (Android 12). ECU: Speeduino 2025.01.6 via módulo Bluetooth HC-06 (ECU_TCC), chave ligada e motor desligado.

| Print | Passo | Status mostrado no app |
|---|---|---|
| `01_app_aberto.png` | App aberto do zero: lista de módulos, botões Buscar/Conectar/Layout, mostradores vazios (`--`) | Desconectado |
| `02_buscando.png` | Toque em **Buscar**: o app procura módulos Bluetooth por perto (botão Buscar desabilitado durante a busca) | Buscando módulos Bluetooth (~12 s)... |
| `03_lista_modulos.png` | Lista de módulos: pareados e novos (marcados `[novo]`) | — |
| `04_ecu_escolhida_busca_terminada.png` | ECU_TCC escolhido; busca terminada | Busca terminada: 11 módulo(s) na lista |
| `05_conectando.png` | Toque em **Conectar** (botão desabilitado enquanto conecta) | — |
| `06_conectado_relogios.png` | Conectado: as 8 grandezas no layout de relógios (motor desligado: 0 rpm, MAP 98 kPa) | Conectado: Speeduino 2025.01.6 |
| `07_layout_numeros.png` | Toque em **Layout**: números grandes | Conectado: Speeduino 2025.01.6 |
| `08_layout_relogios.png` | Toque em **Layout** de novo: volta aos relógios | Conectado: Speeduino 2025.01.6 |
| `09_desconectado.png` | Toque em **Desconectar**: mostradores voltam a `--` e **não** há reconexão automática (desconexão pedida pelo usuário) | Desconectado |

Resultado: **todos os passos funcionaram**. Do conectar até os primeiros valores na tela: menos de 1 minuto (inclui a espera do roteiro).

A reconexão automática (aviso "Conexão perdida – Reconectando... (tentativa N de 10)" com o botão Cancelar) foi vista no teste com o motor funcionando, às 21:20, quando a chave foi desligada. Prints em `TCC/ensaios_2026-11-22/app/`.

Por privacidade, os nomes e endereços Bluetooth de aparelhos pessoais foram borrados nos prints 01, 02 e 03 (no 03 fica visível só o nome do módulo da ECU).

Para repetir o teste: `python teste_app_adb.py <pasta de saída>` com o celular ligado por USB (depuração ativa) e a ECU ligada. O caminho do `adb` fica no início do script.
