# Multi-language concurrency assignments -- nothing is hardcoded.
# Programs are discovered automatically; just drop files in:
#   c/<prog>.c       one program per file      -> c/build/<prog>
#   go/<prog>/       one Go package per dir    -> go/build/<prog>
#   java/<Prog>.java one class per file        -> java/build/ (package auto-detected)
#   python/<prog>.py one script per file
#   ada/<prog>.adb   one program per file      -> ada/build/<prog>
#
# Usage:
#   make                 build + run every program of every language
#   make <lang>          build + run one language: c go java py ada
#   make <lang>-<prog>   build + run a single program, e.g. make ada-tasques
#   make build           compile everything, run nothing
#   make clean           remove all build artifacts
#   make help            list available languages and programs

# --------------- source discovery ---------------
C_SRCS    := $(wildcard c/*.c)
ADA_SRCS  := $(wildcard ada/*.adb)
JAVA_SRCS := $(wildcard java/*.java)
PY_SRCS   := $(wildcard python/*.py)
# one Go package per subdir of go/ (our own go/build/ is excluded)
GO_PKGS   := $(filter-out build,$(patsubst go/%/,%,$(wildcard go/*/)))

# languages that actually exist here
LANGS := $(foreach l,c go java py ada,$(if $(wildcard $l/.),$l))

C_PROGS    := $(patsubst c/%.c,%,$(C_SRCS))
ADA_PROGS  := $(patsubst ada/%.adb,%,$(ADA_SRCS))
JAVA_PROGS := $(patsubst java/%.java,%,$(JAVA_SRCS))
PY_PROGS   := $(patsubst python/%.py,%,$(PY_SRCS))

C_BINS   := $(addprefix c/build/,$(C_PROGS))
ADA_BINS := $(addprefix ada/build/,$(ADA_PROGS))
GO_BINS  := $(addprefix go/build/,$(GO_PKGS))
JAVA_STAMP := java/build/.compiled

# java package prefix is detected per file at run time (empty if the file
# declares no package, i.e. lives in the default package)

# run targets, e.g. c-threads ada-tasques java-Threads py-threads go-threads
C_RUN    := $(addprefix c-,$(C_PROGS))
ADA_RUN  := $(addprefix ada-,$(ADA_PROGS))
JAVA_RUN := $(addprefix java-,$(JAVA_PROGS))
PY_RUN   := $(addprefix py-,$(PY_PROGS))
GO_RUN   := $(addprefix go-,$(GO_PKGS))
ALL_RUNS := $(C_RUN) $(GO_RUN) $(JAVA_RUN) $(PY_RUN) $(ADA_RUN)

all: $(LANGS)

# --------------- language selectors ---------------
c:    $(C_RUN)
go:   $(GO_RUN)
java: $(JAVA_RUN)
py:   $(PY_RUN)
ada:  $(ADA_RUN)

# compile everything without running
build: $(C_BINS) $(ADA_BINS) $(GO_BINS) $(JAVA_STAMP)

# --------------- C ---------------
c/build/%: c/%.c | c/build
	$(CC) -Wall -pthread -o $@ $<

c-%: c/build/%
	c/build/$*

# --------------- Go ---------------
define GO_template
go/build/$(1): $(wildcard go/$(1)/*.go) | go/build
	go build -o $$@ $$^

go-$(1): go/build/$(1)
	go/build/$(1)
endef
$(foreach p,$(GO_PKGS),$(eval $(call GO_template,$(p))))

# --------------- Java ---------------
$(JAVA_STAMP): $(JAVA_SRCS) | java/build
	javac -d java/build $(JAVA_SRCS)
	touch $@

java-%: $(JAVA_STAMP)
	java -cp java/build $$(sed -n 's/^[[:space:]]*package[[:space:]][[:space:]]*\([._[:alnum:]]*\);.*/\1./p' java/$*.java)$*

# --------------- Python ---------------
py-%: python/%.py
	python3 $<

# --------------- Ada ---------------
ada/build/%: ada/%.adb | ada/build
	cd ada/build && gnatmake -q ../$*

ada-%: ada/build/%
	ada/build/$*

# --------------- housekeeping ---------------
BUILD_DIRS := c/build go/build java/build ada/build
$(BUILD_DIRS):
	mkdir -p $@

clean:
	rm -rf $(BUILD_DIRS)

help:
	@echo "Usage: make [all|build|clean|help|<lang>|<lang>-<program>]"
	@echo "Languages: $(LANGS)"
	@echo "Programs:"
	@$(foreach t,$(sort $(ALL_RUNS)),echo "  $(t)";)

.PHONY: all build clean help $(LANGS)
