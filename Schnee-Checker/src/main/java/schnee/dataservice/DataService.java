package schnee.dataservice;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public abstract class DataService {
    public abstract void fillCache(List<double[]> points) throws IOException;
    public abstract void fetchBatch(List<double[]> points) throws IOException;
    public abstract boolean isLoading();
    public abstract Map<String, Double> getCache();
    public abstract String makeKey(double lat, double lon);
    public abstract int getLoadedPoints();
    public abstract int getTotalPoints();

}
