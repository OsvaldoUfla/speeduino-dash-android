package br.ufla.ecutcc.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import br.ufla.ecutcc.modelo.Grandeza;

/** Número grande com rótulo. Em alerta, ganha contorno e número vermelhos. */
public final class MostradorNumero extends Mostrador {

    public MostradorNumero(Context contexto, Grandeza grandeza) {
        super(contexto, grandeza);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        canvas.drawColor(COR_FUNDO);

        if (emAlerta()) {
            pincel.setStyle(Paint.Style.STROKE);
            pincel.setStrokeWidth(Math.min(w, h) * 0.04f);
            pincel.setColor(COR_ALERTA);
            canvas.drawRect(0, 0, w, h, pincel);
        }

        pincel.setStyle(Paint.Style.FILL);
        pincel.setTextAlign(Paint.Align.CENTER);
        pincel.setColor(emAlerta() ? COR_ALERTA : COR_TEXTO);
        pincel.setTextSize(h * 0.40f);
        canvas.drawText(valorFormatado(), w / 2f, h * 0.58f, pincel);

        pincel.setColor(COR_NORMAL);
        pincel.setTextSize(h * 0.15f);
        canvas.drawText(grandeza.rotulo + " " + grandeza.unidade, w / 2f, h * 0.88f, pincel);
    }
}
