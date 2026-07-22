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
%w[/open-api/events/v1/clicks /open-api/events/v1/conversions /open-api/events/v1/receipts/{receiptId} /open-api/rta/v1/decisions].each do |path|
  raise "OpenAPI path missing: #{path}" unless openapi.fetch('paths').key?(path)
end

asyncapi = YAML.load_file(File.join(root, 'asyncapi.yaml'))
raise 'AsyncAPI version must be 3.x' unless asyncapi.fetch('asyncapi').start_with?('3.')
raise 'AsyncAPI channels missing' if asyncapi.fetch('channels').empty?

schema = JSON.parse(File.read(File.join(root, 'event-envelope.schema.json')))
required_fields = %w[schemaVersion eventId taskCode eventType occurredAt device]
missing_fields = required_fields - schema.fetch('required')
raise "event envelope required fields missing: #{missing_fields.join(', ')}" unless missing_fields.empty?
raise 'source must be derived from channel credentials' if schema.fetch('properties').key?('source')
device = schema.fetch('properties').fetch('device')
%w[platform idType hashAlgorithm idHash].each do |field|
  raise "device field missing: #{field}" unless device.fetch('required').include?(field)
end
headers = openapi.fetch('components').fetch('parameters')
%w[ClientId KeyVersion Timestamp Nonce Signature].each do |name|
  raise "auth header missing: #{name}" unless headers.key?(name)
end
puts 'JSON valid: event-envelope.schema.json'
RUBY
