package br.ufla.ecutcc.modelo;

/**
 * As oito grandezas mostradas pelo app (decisão do Osvaldo, 04/11/2026).
 *
 * Cada uma aponta para o NOME do canal no .ini do firmware, não para uma posição
 * fixa: a posição é lida do .ini da versão que estiver gravada na ECU.
 *
 * Faixas de alerta: fora de [alertaMin, alertaMax] o mostrador fica vermelho.
 * NaN = sem limite naquele lado.
 */
public enum Grandeza {
    //        rótulo          unidade  canal no .ini     soma   escalaMin escalaMax alertaMin   alertaMax
    RPM     ("Rotação",       "rpm",  "rpm",             0,     0,        6000,     Double.NaN, Double.NaN),
    MAP     ("MAP",           "kPa",  "map",             0,     0,        110,      Double.NaN, Double.NaN),
    TPS     ("Borboleta",     "%",    "tps",             0,     0,        100,      Double.NaN, Double.NaN),
    // As temperaturas chegam com +40 para caber números negativos num byte sem sinal
    // (no .ini: coolant = { coolantRaw - 40 }).
    CLT     ("Temp. motor",   "°C",   "coolantRaw",      -40,   -40,      120,      Double.NaN, 105),
    IAT     ("Temp. ar",      "°C",   "iatRaw",          -40,   -40,      80,       Double.NaN, Double.NaN),
    BATERIA ("Bateria",       "V",    "batteryVoltage",  0,     8,        16,       11.5,       14.7),
    PW      ("Injeção",       "ms",   "pulseWidth",      0,     0,        25,       Double.NaN, Double.NaN),
    AFR     ("AFR",           "",     "afr",             0,     7,        22,       Double.NaN, Double.NaN);

    public final String rotulo;
    public final String unidade;
    public final String canalIni;
    /** Somado depois da conversão do .ini (ex.: -40 nas temperaturas). */
    public final double soma;
    public final double escalaMin;
    public final double escalaMax;
    public final double alertaMin;
    public final double alertaMax;

    Grandeza(String rotulo, String unidade, String canalIni, double soma,
             double escalaMin, double escalaMax, double alertaMin, double alertaMax) {
        this.rotulo = rotulo;
        this.unidade = unidade;
        this.canalIni = canalIni;
        this.soma = soma;
        this.escalaMin = escalaMin;
        this.escalaMax = escalaMax;
        this.alertaMin = alertaMin;
        this.alertaMax = alertaMax;
    }

    public boolean foraDaFaixa(double valor) {
        return (!Double.isNaN(alertaMin) && valor < alertaMin)
                || (!Double.isNaN(alertaMax) && valor > alertaMax);
    }
}
