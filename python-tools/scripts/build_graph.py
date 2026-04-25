from campusvista_data import graph_summary, validate_all


def main() -> None:
    data, _ = validate_all()
    summary = graph_summary(data)
    print(
        "Graph OK: "
        f"{summary['nodes']} checkpoints, "
        f"{summary['edge_rows']} edge rows, "
        f"{summary['directed_edges']} directed edges after bidirectional expansion"
    )


if __name__ == "__main__":
    main()
