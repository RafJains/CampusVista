package com.example.campusvista;

import android.app.Application;

import com.example.campusvista.data.local.SeedDbCopier;
import com.example.campusvista.data.repository.CheckpointRepository;
import com.example.campusvista.data.repository.MapConfigRepository;
import com.example.campusvista.data.repository.PanoRepository;
import com.example.campusvista.data.repository.PlaceRepository;
import com.example.campusvista.routing.CrowdCostCalculator;
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
        Graph staticGraph = GraphBuilder.getInstance(this).buildStaticGraph();
        CrowdCostCalculator crowdCostCalculator = CrowdCostCalculator.getInstance(this);
        InstructionBuilder instructionBuilder = new InstructionBuilder();
        routePlanner = new RoutePlanner(
                staticGraph,
                crowdCostCalculator,
                instructionBuilder,
                mapConfig.getMetersPerPixel()
        );
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

    public RoutePlanner getRoutePlanner() {
        return routePlanner;
    }

    public NearestCheckpointFinder getNearestCheckpointFinder() {
        return nearestCheckpointFinder;
    }
}
