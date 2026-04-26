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
}
