#!/usr/bin/env python3
"""Build the fixed, local Echo Brain metadata ranker used by the FOSS Android variant."""

from pathlib import Path
import os

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

import tensorflow as tf


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "app" / "src" / "main" / "assets" / "echo_brain_metadata_ranker.tflite"


def main() -> None:
    # Input: radio order, local-profile affinity, same artist, same album, title overlap,
    # and whether local profile evidence exists. The model is an ordering signal only.
    weights = tf.constant([[1.45], [1.10], [0.80], [0.30], [0.35], [0.10]], dtype=tf.float32)
    bias = tf.constant([-1.15], dtype=tf.float32)

    @tf.function(input_signature=[tf.TensorSpec(shape=[None, 6], dtype=tf.float32)])
    def ranker(features: tf.Tensor) -> tf.Tensor:
        return tf.math.sigmoid(tf.linalg.matmul(features, weights) + bias)

    converter = tf.lite.TFLiteConverter.from_concrete_functions([ranker.get_concrete_function()])
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    artifact = converter.convert()
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_bytes(artifact)
    interpreter = tf.lite.Interpreter(model_path=str(OUTPUT))
    interpreter.allocate_tensors()
    assert OUTPUT.stat().st_size > 0
    print(f"Created {OUTPUT.relative_to(ROOT)} ({OUTPUT.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
