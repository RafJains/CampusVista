package com.example.campusvista;

import android.app.Application;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.SeedDbCopier;
import com.example.campusvista.data.repository.CheckpointRepository;
import com.example.campusvista.data.repository.CrowdRepository;
import com.example.campusvista.data.repository.GraphRepository;
import com.example.campusvista.data.repository.MapConfigRepository;
import com.example.campusvista.data.repository.PanoRepository;
import com.example.campusvista.data.repository.PlaceRepository;
import com.example.campusvista.data.repository.RecognitionRepository;
import com.example.campusvista.data.repository.SearchAliasRepository;
import com.example.campusvista.routing.AStarRouter;
import com.example.campusvista.routing.CrowdCostCalculator;
import com.example.campusvista.routing.DijkstraRouter;
import com.example.campusvista.routing.Graph;
import com.example.campusvista.routing.GraphBuilder;
import com.example.campusvista.routing.InstructionBuilder;
import com.example.campusvista.routing.NearestCheckpointFinder;
import com.example.campusvista.routing.RoutePlanner;

public final class CampusVistaApp extends Application {
    private DBHelper dbHelper;
    private MapConfigRepository.MapConfig mapConfig;
    private CheckpointRepository checkpointRepository;
    private PlaceRepository placeRepository;
    private GraphRepository graphRepository;
    private CrowdRepository crowdRepository;
    private PanoRepository panoRepository;
    private RecognitionRepository recognitionRepository;
    private SearchAliasRepository searchAliasRepository;
    private Graph staticGraph;
    private CrowdCostCalculator crowdCostCalculator;
    private InstructionBuilder instructionBuilder;
    private AStarRouter aStarRouter;
    private DijkstraRouter dijkstraRouter;
    private RoutePlanner routePlanner;
    private NearestCheckpointFinder nearestCheckpointFinder;

    @Override
    public void onCreate() {
        super.onCreate();
        SeedDbCopier.copyDatabaseIfNeeded(this);
        dbHelper = DBHelper.getInstance(this);
        mapConfig = MapConfigRepository.getInstance().load(this);
        checkpointRepository = CheckpointRepository.getInstance(this);
        placeRepository = PlaceRepository.getInstance(this);
        graphRepository = GraphRepository.getInstance(this);
        crowdRepository = CrowdRepository.getInstance(this);
        panoRepository = PanoRepository.getInstance(this);
        recognitionRepository = RecognitionRepository.getInstance(this);
        searchAliasRepository = SearchAliasRepository.getInstance(this);
        staticGraph = GraphBuilder.getInstance(this).buildStaticGraph();
        crowdCostCalculator = CrowdCostCalculator.getInstance(this);
        instructionBuilder = new InstructionBuilder();
        aStarRouter = new AStarRouter(
                staticGraph,
                crowdCostCalculator,
                instructionBuilder,
                mapConfig.getMetersPerPixel()
        );
        dijkstraRouter = new DijkstraRouter(
                staticGraph,
                crowdCostCalculator,
                instructionBuilder
        );
        routePlanner = new RoutePlanner(aStarRouter, dijkstraRouter);
        nearestCheckpointFinder = new NearestCheckpointFinder(staticGraph);
    }

    public DBHelper getDbHelper() {
        return dbHelper;
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

    public GraphRepository getGraphRepository() {
        return graphRepository;
    }

    public CrowdRepository getCrowdRepository() {
        return crowdRepository;
    }

    public PanoRepository getPanoRepository() {
        return panoRepository;
    }

    public RecognitionRepository getRecognitionRepository() {
        return recognitionRepository;
    }

    public SearchAliasRepository getSearchAliasRepository() {
        return searchAliasRepository;
    }

    public Graph getStaticGraph() {
        return staticGraph;
    }

    public CrowdCostCalculator getCrowdCostCalculator() {
        return crowdCostCalculator;
    }

    public AStarRouter getAStarRouter() {
        return aStarRouter;
    }

    public DijkstraRouter getDijkstraRouter() {
        return dijkstraRouter;
    }

    public RoutePlanner getRoutePlanner() {
        return routePlanner;
    }

    public NearestCheckpointFinder getNearestCheckpointFinder() {
        return nearestCheckpointFinder;
    }
}
