MAIN := airgradient-to-sensor-community

.PHONY: compile
compile:
	scala-cli compile $(MAIN).sc

.PHONY: format
format:
	scala-cli scalafmt

$(MAIN): $(MAIN).sc
	scala-cli --power package --native-image --force $^ -o $@
