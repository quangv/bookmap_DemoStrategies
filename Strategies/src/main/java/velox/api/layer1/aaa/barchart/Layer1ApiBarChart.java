package velox.api.layer1.aaa.barchart;

import java.awt.Color;

import velox.api.layer1.annotations.Layer1ApiVersion;
import velox.api.layer1.annotations.Layer1ApiVersionValue;
import velox.api.layer1.annotations.Layer1SimpleAttachable;
import velox.api.layer1.annotations.Layer1StrategyName;
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

/**
 * Simple Bar Chart Indicator
 * Displays Open, Close, High, and Low prices as separate lines
 */
@Layer1SimpleAttachable
@Layer1StrategyName("QI Bar Chart")
@Layer1ApiVersion(Layer1ApiVersionValue.VERSION2)
public class Layer1ApiBarChart implements
    CustomModule, BarDataListener, HistoricalDataListener {

    private Indicator openIndicator;
    private Indicator closeIndicator;
    private Indicator highIndicator;
    private Indicator lowIndicator;

    @Override
    public void initialize(String alias, InstrumentInfo info, Api api, InitialState initialState) {
        // Register indicators for Open, Close, High, Low
        openIndicator = api.registerIndicator("Open", GraphType.PRIMARY);
        openIndicator.setColor(new Color(100, 149, 237)); // Cornflower blue
        
        closeIndicator = api.registerIndicator("Close", GraphType.PRIMARY);
        closeIndicator.setColor(new Color(50, 205, 50)); // Lime green
        
        highIndicator = api.registerIndicator("High", GraphType.PRIMARY);
        highIndicator.setColor(new Color(0, 206, 209)); // Dark turquoise
        
        lowIndicator = api.registerIndicator("Low", GraphType.PRIMARY);
        lowIndicator.setColor(new Color(220, 20, 60)); // Crimson red
    }
    
    @Override
    public void stop() {}

    @Override
    public void onBar(OrderBook orderBook, Bar bar) {
        openIndicator.addPoint(bar.getOpen());
        closeIndicator.addPoint(bar.getClose());
        highIndicator.addPoint(bar.getHigh());
        lowIndicator.addPoint(bar.getLow());
    }

    @Override
    public long getInterval() {
        return Intervals.INTERVAL_1_MINUTE;
    }
}
