package com.example.campusvista;

import android.app.Application;

import com.example.campusvista.data.local.SeedDbCopier;
import com.example.campusvista.data.repository.CheckpointRepository;
import com.example.campusvista.data.repository.MapConfigRepository;
import com.example.campusvista.data.repository.PanoRepository;
import com.example.campusvista.data.repository.PlaceRepository;
import com.example.campusvista.data.repository.RecognitionRepository;
import com.example.campusvista.routing.AStarRouter;
import com.example.campusvista.routing.CrowdCostCalculator;
import com.example.campusvista.routing.DijkstraRouter;
import com.example.campusvista.routing.Graph;
import com.example.campusvista.routing.GraphBuilder;
import com.example.campusvista.routing.InstructionBuilder;
import com.example.campusvista.routing.NearestCheckpointFinder;
import com.example.campusvista.routing.RoutePlanner;

public final class CampusVistaApp extends Application {
    private MapConfigRepository.MapConfig mapConfig;
    private CheckpointRepository checkpointRepository;
    private PlaceRepository placeRepository;
    private PanoRepository panoRepository;
    private RecognitionRepository recognitionRepository;
    private RoutePlanner routePlanner;
    private NearestCheckpointFinder nearestCheckpointFinder;

    @Override
    public void onCreate() {
        super.onCreate();
        SeedDbCopier.copyDatabaseIfNeeded(this);
        mapConfig = MapConfigRepository.getInstance().load(this);
        checkpointRepository = CheckpointRepository.getInstance(this);
        placeRepository = PlaceRepository.getInstance(this);
        panoRepository = PanoRepository.getInstance(this);
        recognitionRepository = RecognitionRepository.getInstance(this);
        Graph staticGraph = GraphBuilder.getInstance(this).buildStaticGraph();
        CrowdCostCalculator crowdCostCalculator = CrowdCostCalculator.getInstance(this);
        InstructionBuilder instructionBuilder = new InstructionBuilder();
        AStarRouter aStarRouter = new AStarRouter(
                staticGraph,
                crowdCostCalculator,
                instructionBuilder,
                mapConfig.getMetersPerPixel()
        );
        DijkstraRouter dijkstraRouter = new DijkstraRouter(
                staticGraph,
                crowdCostCalculator,
                instructionBuilder
        );
        routePlanner = new RoutePlanner(aStarRouter, dijkstraRouter);
        nearestCheckpointFinder = new NearestCheckpointFinder(staticGraph);
    }

    public MapConfigRepository.MapConfig getMapConfig() {
        return mapConfig;
    }

    public CheckpointRepository getCheckpointRepository() {
        return checkpointRepository;
    }

    public PlaceRepository getPlaceRepository() {
        return placeRepository;
    }

    public PanoRepository getPanoRepository() {
        return panoRepository;
    }

    public RecognitionRepository getRecognitionRepository() {
        return recognitionRepository;
    }

    public RoutePlanner getRoutePlanner() {
        return routePlanner;
    }

    public NearestCheckpointFinder getNearestCheckpointFinder() {
        return nearestCheckpointFinder;
    }
}
