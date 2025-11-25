package velox.api.layer1.simpledemo.movingaverage;

import java.awt.Color;
import java.util.LinkedList;
import java.util.Queue;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.layers.utils.OrderBook;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.Bar;
import velox.api.layer1.simplified.BarDataListener;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.HistoricalDataListener;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simpledemo.movingaverage.MovingAverageSettings.MAType;

/**
 * Moving Average Indicator for Bookmap using Simplified API
 * Supports SMA, EMA, and WMA calculation methods
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Moving Average")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiMovingAverage implements CustomModule, BarDataListener, HistoricalDataListener {
    
    @Parameter(name = "Period", step = 1.0)
    public Double period = 20.0;
    
    @Parameter(name = "MA Type")
    public String maType = "EMA";
    
    @Parameter(name = "Line Color")
    public Color color = new Color(33, 150, 243);
    
    @Parameter(name = "Interval (seconds)", step = 1.0)
    public Double intervalSeconds = 5.0;
    
    private Indicator maIndicator;
    private MovingAverageCalculator calculator;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        Log.info("QI MA: Initializing for " + alias + ", period=" + period + ", type=" + maType);
        maIndicator = api.registerIndicator("MA", GraphType.PRIMARY);
        maIndicator.setColor(color);
        Log.info("QI MA: Indicator registered, color=" + color);
        MAType type;
        try {
            type = MAType.valueOf(maType);
        } catch (IllegalArgumentException e) {
            Log.warn("QI MA: Invalid MA type '" + maType + "', defaulting to EMA");
            type = MAType.EMA;
        }
        calculator = new MovingAverageCalculator(period.intValue(), type);
    }
    
    @Override
    public void stop() {
        Log.info("QI MA: Stopping");
    }
    
    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        if (calculator == null) {
            Log.warn("QI MA: Calculator is null!");
            return;
        }
        
        // Use close price from bar
        double price = bar.getClose();
        double maValue = calculator.addPrice(price);
        
        // Log first 5 bars, then every 20th
        if (calculator.getCount() <= 5 || calculator.getCount() % 20 == 0) {
            Log.info("QI MA: bar#" + calculator.getCount() + ", close=" + String.format("%.2f", price) + 
                    ", MA=" + (Double.isNaN(maValue) ? "warming up" : String.format("%.2f", maValue)));
        }
        
        if (!Double.isNaN(maValue)) {
            maIndicator.addPoint(maValue);
        }
    }
    
    @Override
    public long getInterval() {
        // Convert seconds to nanoseconds
        return (long)(intervalSeconds * 1_000_000_000L);
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
        private int count = 0;
        
        public MovingAverageCalculator(int period, MAType maType) {
            this.period = period;
            this.maType = maType;
            this.priceQueue = new LinkedList<>();
            this.multiplier = 2.0 / (period + 1.0);
        }
        
        public int getCount() {
            return count;
        }
        
        public double addPrice(double price) {
            if (Double.isNaN(price)) {
                return Double.NaN;
            }
            
            count++;
            
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
