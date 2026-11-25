.PHONY: all ghostlock native-host-tests host-attack-dataflow-test lint-tidy clean

all: ghostlock

ghostlock:
	$(MAKE) -C src ghostlock

native-host-tests:
	$(MAKE) -C src native-host-tests

host-attack-dataflow-test:
	$(MAKE) -C src host-attack-dataflow-test

lint-tidy:
	$(MAKE) -C src lint-tidy

clean:
	$(MAKE) -C src clean
