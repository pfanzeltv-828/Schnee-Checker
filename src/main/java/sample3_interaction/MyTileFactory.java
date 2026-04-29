package sample3_interaction;

import org.jxmapviewer.viewer.DefaultTileFactory;
import org.jxmapviewer.viewer.TileFactoryInfo;

public class MyTileFactory extends DefaultTileFactory {

    public MyTileFactory() {
        super(new TileFactoryInfo(
                1, 17, 17,
                256,
                true, true,
                "https://tile.openstreetmap.org/",  // HTTPS statt HTTP
                "x", "y", "z"
        ) {
            @Override
            public String getTileUrl(int x, int y, int zoom) {
                int z = this.getTotalMapZoom() - zoom;
                return this.getBaseURL() + z + "/" + x + "/" + y + ".png";
            }
        });
    }
}
