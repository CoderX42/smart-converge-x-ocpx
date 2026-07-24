#!/usr/bin/env bash
set -euo pipefail

CONFIGS=(
  "scx-backend/scx-boot/src/main/resources/application-dev.yml"
  "scx-backend/scx-boot/src/main/resources/application-prod.yml"
)

ruby -ryaml - "${CONFIGS[@]}" <<'RUBY'
common_expected = {
  %w[spring data redis host] => '${SCX_REDIS_HOST}',
  %w[spring data redis username] => '${SCX_REDIS_USERNAME}',
  %w[spring data redis password] => '${SCX_REDIS_PASSWORD}',
  %w[spring datasource dynamic datasource master url] => '${SCX_DATASOURCE_URL}',
  %w[spring datasource dynamic datasource master username] => '${SCX_DATASOURCE_USERNAME}',
  %w[spring datasource dynamic datasource master password] => '${SCX_DATASOURCE_PASSWORD}',
}

ARGV.each do |config_path|
  config = YAML.load_file(config_path)
  expected = common_expected.dup
  if config_path.end_with?('application-prod.yml')
    expected[%w[xxl job accessToken]] = '${SCX_XXL_JOB_ACCESS_TOKEN}'
    expected[%w[encode key]] = '${SCX_ENCODE_KEY}'
  end

  expected.each do |path, placeholder|
    actual = path.reduce(config) { |node, key| node.is_a?(Hash) ? node[key] : nil }
    next if actual == placeholder

    abort("Runtime sensitive setting is not injected in #{config_path}: #{path.join('.')}")
  end
end

puts "Development and production connection settings use required environment injection."
RUBY
