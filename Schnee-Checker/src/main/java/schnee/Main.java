package schnee;

import javafx.application.Application;
import javafx.application.Platform;

import javafx.stage.Stage;
import schnee.elevation.ElevationGridBuilder;
import schnee.elevation.ElevationService;
import schnee.server.LocalMapServer;

public class Main extends Application {

    private LocalMapServer server;

    @Override
    public void start(Stage stage) throws Exception {
        ElevationService elevationService = new ElevationService();
        ElevationGridBuilder gridBuilder = new ElevationGridBuilder(elevationService);
        server = new LocalMapServer(gridBuilder, elevationService);
        server.start();

        stage.setOnCloseRequest(e -> {
            server.stop();
            Platform.exit();
        });
    }
}