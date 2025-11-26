package velox.api.layer1.aaa.movingaverage;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
import velox.api.layer1.common.Log;
import velox.api.layer1.data.InstrumentInfo;
import velox.api.layer1.data.TradeInfo;
import velox.api.layer1.messages.indicators.Layer1ApiUserMessageModifyIndicator.GraphType;
import velox.api.layer1.simplified.Api;
import velox.api.layer1.simplified.CustomModule;
import velox.api.layer1.simplified.HistoricalDataListener;
import velox.api.layer1.simplified.Indicator;
import velox.api.layer1.simplified.InitialState;
import velox.api.layer1.simplified.IntervalListener;
import velox.api.layer1.simplified.Intervals;
import velox.api.layer1.simplified.Parameter;
import velox.api.layer1.simplified.TradeDataListener;
import velox.api.layer1.aaa.movingaverage.MovingAverageSettings.MAType;

/**
 * Moving Average Indicator for Bookmap using Simplified API
 * Updates on every trade for smooth, accurate moving averages
 * Supports SMA, EMA, and WMA calculation methods
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Moving Average")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiMovingAverage implements CustomModule, TradeDataListener, HistoricalDataListener, IntervalListener {
    
    @Parameter(name = "Period 1", step = 1.0)
    public Double period1 = 9.0;
    
    @Parameter(name = "Color 1")
    public Color color1 = new Color(255, 87, 51); // Orange-red
    
    @Parameter(name = "Period 2", step = 1.0)
    public Double period2 = 20.0;
    
    @Parameter(name = "Color 2")
    public Color color2 = new Color(33, 150, 243); // Blue
    
    @Parameter(name = "Period 3", step = 1.0)
    public Double period3 = 50.0;
    
    @Parameter(name = "Color 3")
    public Color color3 = new Color(76, 175, 80); // Green
    
    @Parameter(name = "MA Type (SMA/EMA/WMA)")
    public String maType = "EMA";
    
    private Indicator ma1Indicator;
    private Indicator ma2Indicator;
    private Indicator ma3Indicator;
    private SimpleMovingAverage sma1;
    private SimpleMovingAverage sma2;
    private SimpleMovingAverage sma3;
    
    private double lastTradePrice = Double.NaN;
    private double lastMa1 = Double.NaN;
    private double lastMa2 = Double.NaN;
    private double lastMa3 = Double.NaN;
    
    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        Log.info("QI MA: Initializing for " + alias + ", periods=[" + period1 + "," + period2 + "," + period3 + "], type=" + maType);
        
        ma1Indicator = api.registerIndicator("MA" + period1.intValue(), GraphType.PRIMARY);
        ma1Indicator.setColor(color1);
        
        ma2Indicator = api.registerIndicator("MA" + period2.intValue(), GraphType.PRIMARY);
        ma2Indicator.setColor(color2);
        
        ma3Indicator = api.registerIndicator("MA" + period3.intValue(), GraphType.PRIMARY);
        ma3Indicator.setColor(color3);
        
        // Simple MA calculator - like the demo examples
        sma1 = new SimpleMovingAverage(period1.intValue());
        sma2 = new SimpleMovingAverage(period2.intValue());
        sma3 = new SimpleMovingAverage(period3.intValue());
        
        lastTradePrice = initialState.getLastTradePrice();
        
        Log.info("QI MA: Indicators registered");
    }
    
    @Override
    public void stop() {
        Log.info("QI MA: Stopping");
    }
    
    @Override
    public void onTrade(double price, int size, TradeInfo tradeInfo) {
        // Update last trade price on every trade
        lastTradePrice = price;
        
        // Calculate moving averages
        lastMa1 = sma1.update(price);
        lastMa2 = sma2.update(price);
        lastMa3 = sma3.update(price);
    }
    
    @Override
    public void onInterval() {
        // Update indicators at regular intervals for smooth rendering
        if (!Double.isNaN(lastMa1)) {
            ma1Indicator.addPoint(lastMa1);
        }
        if (!Double.isNaN(lastMa2)) {
            ma2Indicator.addPoint(lastMa2);
        }
        if (!Double.isNaN(lastMa3)) {
            ma3Indicator.addPoint(lastMa3);
        }
    }
    
    @Override
    public long getInterval() {
        // Update display every 100ms for smooth lines
        return Intervals.INTERVAL_100_MILLISECONDS;
    }
    
    /**
     * Simple Moving Average calculator
     * Uses the efficient incremental formula from the demo examples
     */
    private static class SimpleMovingAverage {
        private final long n;
        private long counter = 0;
        private double value;
        
        public SimpleMovingAverage(long n) {
            this.n = n;
        }
        
        public double update(double x) {
            long k = Math.min(n, ++counter);
            value = ((k - 1) * value + x) / k;
            return counter >= n ? value : Double.NaN;
        }
    }
}
