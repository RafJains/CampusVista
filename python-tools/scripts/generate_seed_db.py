from campusvista_data import generate_all


def main() -> None:
    outputs = generate_all()
    print("Seed DB generated and Android assets updated:")
    for label, path in outputs.items():
        print(f"- {label}: {path}")


if __name__ == "__main__":
    main()
