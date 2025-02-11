
# Python interpreter
PYTHON = python3
VENV_DIR = python
ACTIVATE = source $(VENV_DIR)/bin/activate
REQUIREMENTS = requirements.txt
SCRIPT = main.py  # Change this to your script name

proto_dir := src/main/proto

#gogo_pb2:
#	protoc -I=${proto_dir} -I=${proto_dir}/gogoproto --python_out=. ${proto_dir}/gogoproto/gogo.proto
#
#types_pb2.py: gogo_pb2
#	protoc -I=${proto_dir} -I=${proto_dir}/gogoproto --python_out=. ${proto_dir}/types.proto
#
#remote_pb2.py: gogo_pb2
#	protoc -I=${proto_dir} -I=${proto_dir}/gogoproto --python_out=. ${proto_dir}/remote.proto
#
#proto: remote_pb2.py types_pb2.py
#

# Default target
all: venv install run

# Create virtual environment
venv:
	$(PYTHON) -m venv $(VENV_DIR)

# Install dependencies
install: venv
	$(ACTIVATE) && pip install --upgrade pip
	$(ACTIVATE) && pip install -r $(REQUIREMENTS)

# Run the script
run: install
	$(ACTIVATE) && $(PYTHON) $(SCRIPT)

# Clean up the virtual environment
clean:
	rm -rf $(VENV_DIR)

# Remove venv and dependencies
reset: clean all
