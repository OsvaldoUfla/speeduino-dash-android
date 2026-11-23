"""Teste automático do app ECU TCC pelo ADB: cada passo gera um print."""
import os
import re
import subprocess
import sys
import time

ADB = r"C:\platform-tools\adb.exe"
OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
PKG = "br.ufla.ecutcc"
passos = []


def adb(*a, capture=True):
    r = subprocess.run([ADB, *a], capture_output=capture, text=True, encoding="utf-8", errors="replace")
    return r.stdout


def tela():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    nos = []
    for m in re.finditer(r'text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        t, x1, y1, x2, y2 = m.group(1), *map(int, m.groups()[1:])
        nos.append((t, (x1 + x2) // 2, (y1 + y2) // 2))
    return nos


def achar(texto, exato=False):
    for t, x, y in tela():
        if (t == texto) if exato else (texto.lower() in t.lower()):
            return t, x, y
    return None


def tocar(texto, exato=False):
    n = achar(texto, exato)
    if not n:
        raise RuntimeError(f"não achei '{texto}' na tela")
    adb("shell", "input", "tap", str(n[1]), str(n[2]))
    return n[0]


def status():
    for t, x, y in tela():
        if any(k in t for k in ("Conectado", "Desconectado", "Falha", "Busca", "Conectando", "Conexão", "Pareando", "Nenhum")):
            return t
    return ""


def foto(nome, descricao):
    n = len(passos) + 1
    arq = f"{n:02d}_{nome}.png"
    adb("shell", "screencap", "-p", "/sdcard/passo.png")
    adb("pull", "/sdcard/passo.png", os.path.join(OUT, arq))
    st = status()
    passos.append((arq, descricao, st))
    print(f"{arq}: {descricao} | status: {st}", flush=True)


def esperar(cond, limite, passo=1.0):
    fim = time.time() + limite
    while time.time() < fim:
        r = cond()
        if r:
            return r
        time.sleep(passo)
    return None


# 0. Se houver o aviso de reconexão aberto, registrar e cancelar
if achar("Conexão perdida", exato=True):
    foto("aviso_reconexao", "Aviso de reconexão automática aberto (conexão perdida)")
    tocar("CANCELAR", exato=True)
    time.sleep(1.5)
    foto("reconexao_cancelada", "Depois de tocar em Cancelar: o app para de tentar e fica em 'Conexão perdida'")

# 1. Abrir o app do zero
adb("shell", "am", "force-stop", PKG)
time.sleep(1)
adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
time.sleep(3)
foto("app_aberto", "App aberto do zero: lista de módulos, botões Buscar/Conectar/Layout e status Desconectado")

# 2. Buscar módulos
tocar("BUSCAR", exato=True)
time.sleep(2)
foto("buscando", "Toque em Buscar: o app procura módulos Bluetooth por perto")
esperar(lambda: "Busca terminada" in status(), 20)
foto("busca_terminada", "Fim da busca: quantidade de módulos encontrados")

# 3. Abrir a lista e escolher o ECU_TCC
n = [x for x in tela() if "(" in x[0] and ":" in x[0]]
if n:
    adb("shell", "input", "tap", str(n[0][1]), str(n[0][2]))
    time.sleep(1.5)
    foto("lista_modulos", "Lista de módulos (pareados e novos)")
    tocar("ECU_TCC")
    time.sleep(1)
foto("ecu_escolhida", "Módulo ECU_TCC escolhido")

# 4. Conectar
tocar("CONECTAR", exato=True)
time.sleep(1)
foto("conectando", "Toque em Conectar")
r = esperar(lambda: ("Conectado" in status() or "Falha" in status() or achar("Não conectou")) and status(), 45)
time.sleep(3)  # dar tempo das leituras aparecerem
foto("conectado_relogios", "Resultado da conexão (layout de relógios, com as 8 grandezas)")

if "Conectado" in status():
    # 5. Trocar layout
    tocar("LAYOUT", exato=True)
    time.sleep(3)
    foto("layout_numeros", "Layout de números grandes")
    tocar("LAYOUT", exato=True)
    time.sleep(3)
    foto("layout_relogios", "De volta ao layout de relógios")
    # 6. Desconectar
    tocar("DESCONECTAR", exato=True)
    time.sleep(2)
    foto("desconectado", "Toque em Desconectar: sem reconexão automática (desconexão pedida pelo usuário)")
else:
    print("CONEXAO_FALHOU", flush=True)

with open(os.path.join(OUT, "LEIA-ME.md"), "w", encoding="utf-8") as f:
    f.write("# Teste automático do app pelo ADB (22/11/2026)\n\n")
    f.write("Roteiro executado pelo ADB (`uiautomator dump` para achar os botões pelo texto, `input tap` para tocar, `screencap` para os prints). Celular: moto g31 (Android 12). ECU: Speeduino 2025.01.6 via HC-06.\n\n")
    f.write("| Print | Passo | Status mostrado no app |\n|---|---|---|\n")
    for arq, d, st in passos:
        f.write(f"| `{arq}` | {d} | {st} |\n")
print("FIM", len(passos), "passos")
