package com.heartratemonitor.heartratemonitor.views;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.heartratemonitor.heartratemonitor.R;

/**
 * Vista personalizada para mostrar visualmente la frecuencia cardíaca
 */
public class HeartRateView extends View {
    
    private Paint backgroundPaint;
    private Paint progressPaint;
    private Paint textPaint;
    private RectF arcRect;
    
    private int maxValue = 220;
    private int minValue = 40;
    private int currentValue = 0;
    
    private int backgroundColor = Color.LTGRAY;
    private int progressColor = Color.RED;
    private int textColor = Color.BLACK;
    
    public HeartRateView(Context context) {
        super(context);
        init(null);
    }
    
    public HeartRateView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }
    
    public HeartRateView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }
    
    private void init(@Nullable AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.HeartRateView);
            
            // Obtener atributos personalizados si están definidos en el XML
            maxValue = a.getInt(R.styleable.HeartRateView_maxValue, maxValue);
            minValue = a.getInt(R.styleable.HeartRateView_minValue, minValue);
            backgroundColor = a.getColor(R.styleable.HeartRateView_backgroundArcColor, backgroundColor);
            progressColor = a.getColor(R.styleable.HeartRateView_progressArcColor, progressColor);
            textColor = a.getColor(R.styleable.HeartRateView_valueTextColor, textColor);
            
            a.recycle();
        }
        
        // Inicializar pinturas
        backgroundPaint = new Paint();
        backgroundPaint.setStyle(Paint.Style.STROKE);
        backgroundPaint.setStrokeWidth(20);
        backgroundPaint.setColor(backgroundColor);
        backgroundPaint.setAntiAlias(true);
        
        progressPaint = new Paint();
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(20);
        progressPaint.setColor(progressColor);
        progressPaint.setAntiAlias(true);
        
        textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setTextSize(50);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);
        
        arcRect = new RectF();
    }
    
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // Asegurar que la vista sea un cuadrado
        int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), 
                         MeasureSpec.getSize(heightMeasureSpec));
        
        setMeasuredDimension(size, size);
    }
    
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        
        // Actualizar el rectángulo del arco basado en el tamaño actual
        int padding = (int) (Math.max(backgroundPaint.getStrokeWidth(), 
                                      progressPaint.getStrokeWidth()) / 2) + 10;
        arcRect.set(padding, padding, w - padding, h - padding);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // Dibujar arco de fondo
        canvas.drawArc(arcRect, 135, 270, false, backgroundPaint);
        
        // Calcular y dibujar el arco de progreso
        if (currentValue > 0) {
            float sweepAngle = calculateSweepAngle();
            canvas.drawArc(arcRect, 135, sweepAngle, false, progressPaint);
        }
        
        // Dibujar texto del valor
        if (currentValue > 0) {
            String valueText = String.valueOf(currentValue);
            int xPos = getWidth() / 2;
            int yPos = (int) ((getHeight() / 2) - ((textPaint.descent() + textPaint.ascent()) / 2));
            canvas.drawText(valueText, xPos, yPos, textPaint);
        }
    }
    
    private float calculateSweepAngle() {
        // Convertir el valor actual a un ángulo de barrido entre 0 y 270 grados
        float percentage = (float) (currentValue - minValue) / (maxValue - minValue);
        percentage = Math.max(0, Math.min(1, percentage)); // Limitar entre 0 y 1
        return percentage * 270;
    }
    
    /**
     * Establece el valor actual de la frecuencia cardíaca y actualiza la vista
     * @param value Valor de frecuencia cardíaca
     */
    public void setValue(int value) {
        this.currentValue = Math.max(minValue, Math.min(maxValue, value));
        invalidate(); // Solicitar redibujo de la vista
    }
    
    /**
     * Establece el valor máximo
     * @param maxValue Valor máximo
     */
    public void setMaxValue(int maxValue) {
        this.maxValue = maxValue;
        invalidate();
    }
    
    /**
     * Establece el valor mínimo
     * @param minValue Valor mínimo
     */
    public void setMinValue(int minValue) {
        this.minValue = minValue;
        invalidate();
    }
    
    /**
     * Establece el color del arco de fondo
     * @param color Color en formato ARGB
     */
    public void setBackgroundArcColor(int color) {
        this.backgroundColor = color;
        backgroundPaint.setColor(color);
        invalidate();
    }
    
    /**
     * Establece el color del arco de progreso
     * @param color Color en formato ARGB
     */
    public void setProgressArcColor(int color) {
        this.progressColor = color;
        progressPaint.setColor(color);
        invalidate();
    }
} 