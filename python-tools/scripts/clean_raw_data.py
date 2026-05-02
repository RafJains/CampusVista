from campusvista_data import validate_all, write_processed_outputs


def main() -> None:
    data, config = validate_all()
    write_processed_outputs(data, config)
    print("Processed JSON files written to python-tools/data/processed")


if __name__ == "__main__":
    main()
