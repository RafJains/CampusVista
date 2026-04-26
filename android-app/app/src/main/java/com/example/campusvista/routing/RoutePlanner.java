package com.example.campusvista.routing;

public final class RoutePlanner {
    private final AStarRouter aStarRouter;
    private final DijkstraRouter dijkstraRouter;

    public RoutePlanner(AStarRouter aStarRouter, DijkstraRouter dijkstraRouter) {
        this.aStarRouter = aStarRouter;
        this.dijkstraRouter = dijkstraRouter;
    }

    public RouteResult computeRoute(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            String destinationPlaceName
    ) {
        RouteResult aStarResult = aStarRouter.computeRoute(
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                destinationPlaceName
        );
        if (aStarResult.isRouteFound()) {
            return aStarResult;
        }
        return dijkstraRouter.computeRoute(
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                destinationPlaceName
        );
    }
}
