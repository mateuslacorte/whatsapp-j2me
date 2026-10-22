FROM j2me-compiler:local

WORKDIR /workspace

ENTRYPOINT ["bash", "/workspace/build.sh"]
