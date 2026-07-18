#!/usr/bin/env bash
set -euo pipefail

PROD_CONFIG="scx-backend/scx-boot/src/main/resources/application-prod.yml"

ruby -ryaml - "$PROD_CONFIG" <<'RUBY'
config_path = ARGV.fetch(0)
config = YAML.load_file(config_path)

expected = {
  %w[spring data redis password] => '${SCX_REDIS_PASSWORD}',
  %w[spring datasource dynamic datasource master password] => '${SCX_DATASOURCE_PASSWORD}',
  %w[xxl job accessToken] => '${SCX_XXL_JOB_ACCESS_TOKEN}',
  %w[encode key] => '${SCX_ENCODE_KEY}',
}

expected.each do |path, placeholder|
  actual = path.reduce(config) { |node, key| node.is_a?(Hash) ? node[key] : nil }
  next if actual == placeholder

  abort("Production sensitive setting is not injected: #{path.join('.')}")
end

puts "Production sensitive settings use required environment injection."
RUBY
