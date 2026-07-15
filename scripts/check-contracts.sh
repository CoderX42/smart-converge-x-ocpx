#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
contracts_dir="$root_dir/docs/广告平台设计/contracts"

ruby -rjson -ryaml - <<'RUBY'
root = 'docs/广告平台设计/contracts'
Dir.glob(File.join(root, '*.yaml')).sort.each do |path|
  YAML.load_file(path)
  puts "YAML valid: #{path}"
end

JSON.parse(File.read(File.join(root, 'event-envelope.schema.json')))
puts 'JSON valid: event-envelope.schema.json'
RUBY
