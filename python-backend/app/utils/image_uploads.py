from __future__ import annotations


def extract_image_upload(body: bytes, content_type: str) -> tuple[bytes, str]:
    normalized_type = content_type.split(";")[0].strip().lower()
    if normalized_type.startswith("image/"):
        return body, normalized_type
    if normalized_type != "multipart/form-data":
        raise ValueError("Upload must be a multipart image or raw image body.")

    boundary = _multipart_boundary(content_type)
    delimiter = b"--" + boundary
    for part in body.split(delimiter):
        if b"\r\n\r\n" not in part:
            continue
        header_block, payload = part.split(b"\r\n\r\n", 1)
        headers = header_block.decode("utf-8", errors="ignore").lower()
        if "content-disposition:" not in headers:
            continue
        if 'name="image"' not in headers and "filename=" not in headers:
            continue
        part_type = _part_content_type(headers)
        return _trim_multipart_payload(payload), part_type
    raise ValueError("Multipart upload must include an image file field.")


def _part_content_type(headers: str) -> str:
    for line in headers.splitlines():
        if line.startswith("content-type:"):
            return line.split(":", 1)[1].strip()
    return "application/octet-stream"


def _trim_multipart_payload(payload: bytes) -> bytes:
    payload = payload.rstrip(b"\r\n")
    if payload.endswith(b"--"):
        payload = payload[:-2].rstrip(b"\r\n")
    return payload


def _multipart_boundary(content_type: str) -> bytes:
    for item in content_type.split(";"):
        item = item.strip()
        if item.startswith("boundary="):
            return item.split("=", 1)[1].strip('"').encode("utf-8")
    raise ValueError("Multipart upload is missing a boundary.")
