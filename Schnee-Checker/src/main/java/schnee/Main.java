package schnee;

import javafx.application.Application;
import javafx.application.Platform;

import javafx.stage.Stage;
import schnee.dataservice.ElevationService;
import schnee.dataservice.GridBuilder;
import schnee.dataservice.SnowDepthService;
import schnee.server.LocalMapServer;

public class Main extends Application {

    LocalMapServer app;

    @Override
    public void start(Stage stage) throws Exception {
        ElevationService elevationService = new ElevationService();
        SnowDepthService snowDepthService = new SnowDepthService();
        GridBuilder gridBuilder = new GridBuilder(elevationService,snowDepthService);

        app = new LocalMapServer(gridBuilder, elevationService, snowDepthService);
        app.start();

        stage.setOnCloseRequest(e -> {
            app.stop();
            Platform.exit();
        });
    }
}