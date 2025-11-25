package velox.api.layer1.simpledemo.movingaverage;

import java.awt.Color;
import java.util.LinkedList;
import java.util.Queue;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simplified.TradeDataListener;
import velox.api.layer1.simpledemo.movingaverage.MovingAverageSettings.MAType;

/**
 * Moving Average Indicator for Bookmap using Simplified API
 * Supports SMA, EMA, and WMA calculation methods
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Moving Average")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiMovingAverage implements CustomModule, TradeDataListener {
    
    @Parameter(name = "Period", step = 1.0)
    public Double period = 20.0;
    
    @Parameter(name = "MA Type")
    public String maType = "EMA";
    
    @Parameter(name = "Line Color")
    public Color color = new Color(33, 150, 243);
    
    private Indicator maIndicator;
    private MovingAverageCalculator calculator;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        maIndicator = api.registerIndicator("MA", GraphType.PRIMARY);
        maIndicator.setColor(color);
        MAType type = MAType.valueOf(maType);
        calculator = new MovingAverageCalculator(period.intValue(), type);
    }
    
    @Override
    public void stop() {
        // Cleanup if needed
    }
    
    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        double maValue = calculator.addPrice(price);
        if (!Double.isNaN(maValue)) {
            maIndicator.addPoint(maValue);
        }
    }
    
    /**
     * Helper class to calculate moving averages
     */
    private static class MovingAverageCalculator {
        private final int period;
        private final MAType maType;
        private final Queue<Double> priceQueue;
        private double sum = 0.0;
        private double ema = Double.NaN;
        private final double multiplier;
        
        public MovingAverageCalculator(int period, MAType maType) {
            this.period = period;
            this.maType = maType;
            this.priceQueue = new LinkedList<>();
            this.multiplier = 2.0 / (period + 1.0);
        }
        
        public double addPrice(double price) {
            if (Double.isNaN(price)) {
                return Double.NaN;
            }
            
            switch (maType) {
                case SMA:
                    return calculateSMA(price);
                case EMA:
                    return calculateEMA(price);
                case WMA:
                    return calculateWMA(price);
                default:
                    return calculateSMA(price);
            }
        }
        
        private double calculateSMA(double price) {
            priceQueue.offer(price);
            sum += price;
            
            if (priceQueue.size() > period) {
                double oldPrice = priceQueue.poll();
                sum -= oldPrice;
            }
            
            if (priceQueue.size() < period) {
                return Double.NaN;
            }
            
            return sum / period;
        }
        
        private double calculateEMA(double price) {
            priceQueue.offer(price);
            
            if (priceQueue.size() < period) {
                // Calculate SMA for initialization
                sum += price;
                if (priceQueue.size() == period) {
                    ema = sum / period;
                    return ema;
                }
                return Double.NaN;
            }
            
            if (priceQueue.size() > period) {
                priceQueue.poll();
            }
            
            // EMA formula: EMA = (Price - PreviousEMA) * multiplier + PreviousEMA
            ema = (price - ema) * multiplier + ema;
            return ema;
        }
        
        private double calculateWMA(double price) {
            priceQueue.offer(price);
            
            if (priceQueue.size() > period) {
                priceQueue.poll();
            }
            
            if (priceQueue.size() < period) {
                return Double.NaN;
            }
            
            // WMA calculation: sum of (price * weight) / sum of weights
            double weightedSum = 0.0;
            double weightSum = 0.0;
            int weight = 1;
            
            for (Double p : priceQueue) {
                weightedSum += p * weight;
                weightSum += weight;
                weight++;
            }
            
            return weightedSum / weightSum;
        }
    }
}
