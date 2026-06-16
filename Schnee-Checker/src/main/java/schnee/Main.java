package schnee;

import javafx.application.Application;
import javafx.application.Platform;

import javafx.stage.Stage;
import schnee.elevation.ElevationGridBuilder;
import schnee.elevation.ElevationService;
import schnee.server.LocalMapServer;

public class Main extends Application {

    LocalMapServer app;

    @Override
    public void start(Stage stage) throws Exception {
        ElevationService elevationService = new ElevationService();
        ElevationGridBuilder gridBuilder = new ElevationGridBuilder(elevationService);

        app = new LocalMapServer(gridBuilder, elevationService);
        app.start();

        stage.setOnCloseRequest(e -> {
            app.stop();
            Platform.exit();
        });
    }
}