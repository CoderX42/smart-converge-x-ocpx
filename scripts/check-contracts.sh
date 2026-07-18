#!/usr/bin/env bash
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
contracts_dir="$root_dir/docs/广告平台设计/contracts"

ruby -rjson -ryaml - <<'RUBY'
root = 'docs/广告平台设计/contracts'
required_yaml = %w[openapi.yaml asyncapi.yaml]
required_yaml.each do |name|
  path = File.join(root, name)
  raise "missing contract: #{name}" unless File.file?(path)
  document = YAML.load_file(path)
  raise "invalid contract root: #{name}" unless document.is_a?(Hash)
  puts "YAML valid: #{path}"
end

openapi = YAML.load_file(File.join(root, 'openapi.yaml'))
raise 'OpenAPI version must be 3.x' unless openapi.fetch('openapi').start_with?('3.')
%w[/open-api/events/v1/clicks /open-api/events/v1/conversions].each do |path|
  raise "OpenAPI path missing: #{path}" unless openapi.fetch('paths').key?(path)
end

asyncapi = YAML.load_file(File.join(root, 'asyncapi.yaml'))
raise 'AsyncAPI version must be 3.x' unless asyncapi.fetch('asyncapi').start_with?('3.')
raise 'AsyncAPI channels missing' if asyncapi.fetch('channels').empty?

schema = JSON.parse(File.read(File.join(root, 'event-envelope.schema.json')))
required_fields = %w[schemaVersion eventId source taskCode eventType occurredAt deviceKeyHash]
missing_fields = required_fields - schema.fetch('required')
raise "event envelope required fields missing: #{missing_fields.join(', ')}" unless missing_fields.empty?
puts 'JSON valid: event-envelope.schema.json'
RUBY
