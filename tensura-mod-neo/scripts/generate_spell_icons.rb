#!/usr/bin/env ruby

require "json"
require "zlib"

SIZE = 32
ROOT = File.expand_path("..", __dir__)
SPELL_DIR = File.join(ROOT, "src/main/resources/data/tensura/spells")
OUTPUT_DIR = File.join(ROOT, "src/main/resources/assets/tensura/textures/item/spell")

TARGETS = {
  "aerial_ace" => :cross_wing,
  "air_cutter" => :triple_blade,
  "aqua_jet" => :drop_arrow,
  "aurora_veil" => :curtain,
  "blizzard" => :snowstorm,
  "bug_buzz" => :buzz,
  "bulldoze" => :earth_wedge,
  "charm" => :heart,
  "dazzling_gleam" => :dazzle,
  "dig" => :burrow,
  "draco_meteor" => :meteor,
  "draining_kiss" => :kiss,
  "earth_power" => :eruption,
  "earthquake" => :quake,
  "electro_ball" => :electric_orb,
  "ember" => :ember,
  "fairy_wind" => :fairy_swirl,
  "future_sight" => :eye,
  "gust" => :gust,
  "hurricane" => :hurricane,
  "hydro_pump" => :water_cannon,
  "hyper_voice" => :voice,
  "ice_beam" => :ice_ray,
  "iron_defense" => :shield,
  "moonblast" => :moon,
  "mud_shot" => :mud,
  "pin_missile" => :needles,
  "quick_attack" => :speed,
  "rest" => :rest,
  "string_shot" => :web,
  "sucker_punch" => :fist,
  "swift" => :star,
  "tailwind" => :feather,
  "thunder" => :thunder,
  "tri_attack" => :triad,
  "u_turn" => :return,
  "vine_whip" => :vine,
  "whirlpool" => :whirlpool,
  "x_scissor" => :scissor
}.freeze

ICON_ORDER = %w[
  flamethrower surf toxic_spikes close_combat shadow_sneak psybeam volt_tackle
  fire_spin rock_slide recover dark_pulse aqua_jet aurora_veil blizzard
  draco_meteor electro_ball ember future_sight hydro_pump ice_beam iron_defense
  quick_attack rest string_shot sucker_punch thunder tri_attack vine_whip
  whirlpool pin_missile u_turn x_scissor bug_buzz mud_shot bulldoze dig
  earth_power earthquake fairy_wind draining_kiss charm dazzling_gleam moonblast
  gust air_cutter aerial_ace tailwind hurricane swift hyper_voice
].freeze

PALETTES = {
  "normal" => [[242, 240, 230, 255], [155, 151, 139, 255]],
  "fire" => [[255, 102, 45, 255], [255, 202, 74, 255]],
  "water" => [[45, 169, 255, 255], [91, 235, 238, 255]],
  "electric" => [[255, 224, 52, 255], [255, 255, 188, 255]],
  "grass" => [[89, 214, 91, 255], [188, 255, 120, 255]],
  "ice" => [[130, 232, 255, 255], [231, 255, 255, 255]],
  "fighting" => [[245, 74, 77, 255], [255, 190, 122, 255]],
  "poison" => [[197, 91, 222, 255], [142, 245, 117, 255]],
  "ground" => [[211, 148, 76, 255], [255, 214, 126, 255]],
  "flying" => [[151, 213, 255, 255], [245, 252, 255, 255]],
  "psychic" => [[245, 94, 184, 255], [112, 232, 255, 255]],
  "bug" => [[157, 211, 59, 255], [231, 255, 138, 255]],
  "rock" => [[190, 162, 83, 255], [238, 218, 153, 255]],
  "ghost" => [[126, 101, 202, 255], [210, 170, 255, 255]],
  "dragon" => [[116, 91, 255, 255], [255, 91, 98, 255]],
  "dark" => [[103, 91, 112, 255], [221, 105, 162, 255]],
  "steel" => [[164, 190, 205, 255], [238, 250, 255, 255]],
  "fairy" => [[255, 135, 207, 255], [255, 235, 250, 255]]
}.freeze

class Canvas
  def initialize(primary, secondary)
    @pixels = Array.new(SIZE * SIZE) { [0, 0, 0, 0] }
    @primary = primary
    @secondary = secondary
    disc
  end

  def pixel(x, y, color)
    return unless x.between?(0, SIZE - 1) && y.between?(0, SIZE - 1)
    @pixels[y * SIZE + x] = color
  end

  def dot(x, y, color = @primary, radius = 1)
    (-radius..radius).each do |dy|
      (-radius..radius).each do |dx|
        pixel(x + dx, y + dy, color) if dx * dx + dy * dy <= radius * radius
      end
    end
  end

  def line(x0, y0, x1, y1, color = @primary, width = 1)
    dx = (x1 - x0).abs
    sx = x0 < x1 ? 1 : -1
    dy = -(y1 - y0).abs
    sy = y0 < y1 ? 1 : -1
    error = dx + dy
    loop do
      dot(x0, y0, color, width - 1)
      break if x0 == x1 && y0 == y1
      doubled = 2 * error
      if doubled >= dy
        error += dy
        x0 += sx
      end
      if doubled <= dx
        error += dx
        y0 += sy
      end
    end
  end

  def arc(cx, cy, radius, from, to, color = @primary, width = 1)
    steps = [12, ((to - from).abs * radius / 3.0).ceil].max
    points = (0..steps).map do |index|
      angle = from + (to - from) * index / steps
      [cx + Math.cos(angle) * radius, cy + Math.sin(angle) * radius]
    end
    points.each_cons(2) do |left, right|
      line(left[0].round, left[1].round, right[0].round, right[1].round, color, width)
    end
  end

  def render(symbol)
    send(symbol)
    @pixels
  end

  private

  def disc
    center = (SIZE - 1) / 2.0
    SIZE.times do |y|
      SIZE.times do |x|
        distance = Math.sqrt((x - center)**2 + (y - center)**2)
        pixel(x, y, [8, 12, 18, 255]) if distance <= 13.2
        pixel(x, y, @secondary) if distance.between?(11.2, 12.8)
        pixel(x, y, @primary) if distance.between?(12.8, 13.7)
      end
    end
    dot(7, 8, [255, 255, 255, 170], 0)
    dot(24, 23, @secondary, 0)
  end

  def cross_wing
    line(8, 20, 23, 11, @primary, 2); line(9, 11, 23, 20, @secondary, 2)
    line(7, 16, 12, 16, @primary); line(20, 16, 25, 16, @secondary)
  end

  def triple_blade
    line(8, 11, 23, 8, @secondary, 2); line(7, 16, 24, 13, @primary, 2)
    line(8, 21, 23, 18, @secondary, 2)
  end

  def drop_arrow
    line(16, 7, 10, 17, @secondary, 2); line(10, 17, 16, 24, @primary, 2)
    line(16, 24, 22, 17, @primary, 2); line(22, 17, 16, 7, @secondary, 2)
    line(9, 16, 23, 16, @primary); line(20, 13, 23, 16, @primary); line(20, 19, 23, 16, @primary)
  end

  def curtain
    arc(16, 12, 7, Math::PI, Math::PI * 2, @secondary, 2)
    line(9, 12, 9, 22, @primary); line(13, 10, 13, 20, @secondary)
    line(18, 10, 18, 20, @primary); line(23, 12, 23, 22, @secondary)
  end

  def snowstorm
    line(16, 7, 16, 25, @primary); line(8, 11, 24, 21, @secondary)
    line(8, 21, 24, 11, @primary); arc(16, 16, 6, 0, Math::PI * 2, @secondary)
  end

  def buzz
    arc(16, 16, 4, 0, Math::PI * 2, @primary, 2)
    arc(16, 16, 8, -0.8, 0.8, @secondary, 2); arc(16, 16, 8, 2.3, 3.9, @secondary, 2)
  end

  def earth_wedge
    line(7, 22, 25, 22, @secondary, 2); line(9, 20, 22, 9, @primary, 2)
    line(22, 9, 25, 22, @primary, 2); line(12, 18, 22, 18, @secondary)
  end

  def heart
    arc(12, 13, 4, 3.3, 6.2, @primary, 2); arc(20, 13, 4, 3.2, 6.1, @primary, 2)
    line(8, 14, 16, 24, @primary, 2); line(24, 14, 16, 24, @secondary, 2)
  end

  def dazzle
    line(16, 6, 16, 26, @primary, 2); line(6, 16, 26, 16, @primary, 2)
    line(10, 10, 22, 22, @secondary); line(10, 22, 22, 10, @secondary)
  end

  def burrow
    line(16, 7, 16, 21, @primary, 2); line(10, 16, 16, 23, @primary, 2); line(22, 16, 16, 23, @primary, 2)
    line(8, 25, 24, 25, @secondary, 2)
  end

  def meteor
    line(7, 8, 18, 17, @secondary, 2); line(10, 6, 20, 16, @primary)
    dot(21, 20, @primary, 4); dot(22, 19, @secondary, 2)
  end

  def kiss
    heart; line(9, 24, 23, 24, @secondary); dot(12, 27, @primary, 1); dot(20, 27, @primary, 1)
  end

  def eruption
    line(9, 24, 13, 15, @secondary, 2); line(13, 15, 16, 21, @primary, 2)
    line(16, 21, 20, 11, @primary, 2); line(20, 11, 24, 24, @secondary, 2)
    line(16, 8, 16, 13, @primary); line(11, 10, 13, 14, @secondary); line(22, 8, 20, 13, @secondary)
  end

  def quake
    line(7, 10, 13, 14, @secondary, 2); line(13, 14, 10, 18, @primary, 2)
    line(10, 18, 17, 22, @primary, 2); line(17, 22, 21, 16, @secondary, 2); line(21, 16, 25, 20, @primary, 2)
  end

  def electric_orb
    arc(16, 16, 8, 0, Math::PI * 2, @secondary, 2)
    line(17, 7, 12, 16, @primary, 2); line(12, 16, 18, 16, @primary, 2); line(18, 16, 14, 25, @primary, 2)
  end

  def ember
    arc(16, 17, 7, -0.2, 3.4, @primary, 2); line(10, 17, 17, 7, @secondary, 2)
    line(17, 7, 18, 15, @primary, 2); arc(16, 18, 3, 0, Math::PI * 2, @secondary)
  end

  def fairy_swirl
    arc(16, 16, 8, -1.2, 3.7, @primary, 2); arc(16, 16, 4, 1.7, 6.1, @secondary, 2)
    dot(23, 8, @primary, 1); dot(8, 21, @secondary, 1)
  end

  def eye
    arc(16, 16, 9, 3.5, 5.9, @primary, 2); arc(16, 16, 9, 0.35, 2.75, @secondary, 2)
    dot(16, 16, @primary, 3); dot(17, 15, [255, 255, 255, 255], 1)
  end

  def gust
    arc(13, 12, 7, -1.2, 1.2, @primary, 2); line(7, 12, 19, 12, @primary)
    arc(17, 19, 6, -1.1, 1.1, @secondary, 2); line(8, 19, 22, 19, @secondary)
  end

  def hurricane
    arc(16, 15, 9, -1.1, 4.8, @primary, 2); arc(16, 16, 5, 1.0, 6.0, @secondary, 2)
    dot(16, 16, [255, 255, 255, 255], 1)
  end

  def water_cannon
    line(7, 18, 23, 12, @primary, 2); line(8, 22, 24, 16, @secondary, 2)
    dot(24, 14, @primary, 2); line(9, 16, 6, 12, @secondary)
  end

  def voice
    arc(10, 16, 5, -0.8, 0.8, @primary, 2); arc(10, 16, 9, -0.7, 0.7, @secondary, 2)
    arc(10, 16, 13, -0.6, 0.6, @primary, 2); line(6, 13, 6, 19, @secondary, 2)
  end

  def ice_ray
    line(7, 22, 24, 9, @primary, 2); line(11, 23, 25, 13, @secondary)
    line(21, 7, 21, 14, @secondary); line(17, 10, 24, 10, @secondary)
  end

  def shield
    line(16, 7, 8, 10, @secondary, 2); line(8, 10, 10, 21, @primary, 2)
    line(10, 21, 16, 25, @primary, 2); line(16, 25, 22, 21, @secondary, 2); line(22, 21, 24, 10, @secondary, 2); line(24, 10, 16, 7, @primary, 2)
  end

  def moon
    arc(17, 16, 9, -1.45, 1.45, @primary, 2); arc(12, 16, 8, -1.25, 1.25, @secondary, 2)
    dot(23, 9, [255, 255, 255, 255], 1)
  end

  def mud
    dot(15, 16, @primary, 6); dot(18, 13, @secondary, 3); dot(9, 23, @primary, 1); dot(23, 22, @secondary, 1)
  end

  def needles
    line(8, 22, 14, 8, @primary, 2); line(14, 24, 18, 7, @secondary, 2); line(20, 23, 24, 10, @primary, 2)
  end

  def speed
    line(7, 12, 19, 12, @secondary, 2); line(12, 8, 23, 16, @primary, 2)
    line(23, 16, 12, 24, @primary, 2); line(7, 20, 19, 20, @secondary, 2)
  end

  def rest
    line(9, 10, 20, 10, @primary, 2); line(20, 10, 10, 20, @primary, 2); line(10, 20, 21, 20, @secondary, 2)
    line(19, 7, 24, 7, @secondary); line(24, 7, 20, 12, @secondary)
  end

  def web
    line(16, 7, 16, 25, @primary); line(7, 16, 25, 16, @primary)
    line(9, 9, 23, 23, @secondary); line(9, 23, 23, 9, @secondary)
    arc(16, 16, 6, 0, Math::PI * 2, @primary); arc(16, 16, 10, 0, Math::PI * 2, @secondary)
  end

  def fist
    line(9, 14, 23, 14, @primary, 2); line(10, 14, 11, 23, @secondary, 2)
    line(11, 23, 20, 23, @primary, 2); line(20, 23, 24, 17, @secondary, 2)
    line(12, 9, 12, 14, @primary, 2); line(17, 8, 17, 14, @secondary, 2); line(22, 10, 22, 15, @primary, 2)
  end

  def star
    line(16, 6, 19, 13, @primary, 2); line(19, 13, 26, 14, @secondary, 2)
    line(26, 14, 21, 19, @primary, 2); line(21, 19, 23, 26, @secondary, 2)
    line(23, 26, 16, 22, @primary, 2); line(16, 22, 9, 26, @secondary, 2)
    line(9, 26, 11, 19, @primary, 2); line(11, 19, 6, 14, @secondary, 2); line(6, 14, 13, 13, @primary, 2); line(13, 13, 16, 6, @secondary, 2)
  end

  def feather
    arc(13, 13, 9, -0.8, 1.8, @primary, 2); line(9, 24, 22, 8, @secondary, 2)
    line(12, 19, 8, 17, @primary); line(16, 15, 22, 14, @primary); line(18, 11, 23, 10, @secondary)
  end

  def thunder
    line(19, 6, 10, 16, @secondary, 2); line(10, 16, 17, 16, @primary, 2)
    line(17, 16, 12, 26, @primary, 2); line(12, 26, 24, 13, @secondary, 2); line(24, 13, 18, 13, @primary, 2)
  end

  def triad
    line(16, 7, 7, 23, @primary, 2); line(7, 23, 25, 23, @secondary, 2); line(25, 23, 16, 7, @primary, 2)
    dot(16, 12, @secondary, 1); dot(11, 20, @primary, 1); dot(21, 20, @secondary, 1)
  end

  def return
    arc(16, 16, 8, -0.2, 4.7, @primary, 2); line(16, 8, 21, 8, @secondary, 2)
    line(16, 8, 18, 13, @secondary, 2)
  end

  def vine
    arc(13, 18, 8, -1.5, 1.7, @primary, 2); arc(19, 14, 7, 1.7, 4.8, @secondary, 2)
    line(9, 19, 6, 15, @primary); line(21, 11, 25, 8, @secondary)
  end

  def whirlpool
    arc(16, 16, 9, -0.5, 5.0, @primary, 2); arc(16, 16, 5, 1.0, 6.1, @secondary, 2)
    dot(16, 16, @primary, 1)
  end

  def scissor
    line(8, 8, 24, 24, @primary, 2); line(24, 8, 8, 24, @secondary, 2)
    dot(10, 10, @secondary, 2); dot(22, 10, @primary, 2)
  end
end

def png_chunk(type, data)
  binary_type = type.b
  [data.bytesize].pack("N") + binary_type + data +
    [Zlib.crc32(binary_type + data)].pack("N")
end

def write_png(path, pixels)
  rows = pixels.each_slice(SIZE).map { |row| "\x00".b + row.flatten.pack("C*") }.join
  header = [SIZE, SIZE, 8, 6, 0, 0, 0].pack("NNC5")
  png = "\x89PNG\r\n\x1a\n".b + png_chunk("IHDR", header)
  png << png_chunk("IDAT", Zlib::Deflate.deflate(rows, Zlib::BEST_COMPRESSION))
  png << png_chunk("IEND", "")
  File.binwrite(path, png)
end

generated = []
TARGETS.each do |spell, symbol|
  output = File.join(OUTPUT_DIR, "#{spell}.png")
  next if File.exist?(output)

  definition = JSON.parse(File.read(File.join(SPELL_DIR, "#{spell}.json")))
  palette = PALETTES.fetch(definition.fetch("pokemon_type"))
  write_png(output, Canvas.new(*palette).render(symbol))
  generated << spell
end

model_directory = File.join(ROOT, "src/main/resources/assets/tensura/models/item")
ICON_ORDER.each do |spell|
  spell_model = {
    "parent" => "item/generated",
    "textures" => { "layer0" => "tensura:item/spell/#{spell}" }
  }
  File.write(File.join(model_directory, "spell_#{spell}.json"),
    JSON.pretty_generate(spell_model) + "\n")
  File.write(File.join(model_directory, "spell_icon_#{spell}.json"),
    JSON.pretty_generate("parent" => "tensura:item/spell_#{spell}") + "\n")
end

school_models = %w[
  lightning fire water ice shadow psychic dragon nature poison earth wind fairy steel
]
overrides = school_models.each_with_index.map do |school, index|
  { "predicate" => { "tensura:school" => index + 1 },
    "model" => "tensura:item/spell_#{school}" }
end
overrides.concat(ICON_ORDER.each_with_index.map do |spell, index|
  { "predicate" => { "tensura:icon" => index + 1 },
    "model" => "tensura:item/spell_#{spell}" }
end)
spell_item_model = {
  "parent" => "item/generated",
  "textures" => { "layer0" => "cobblemon:item/type_gem/fighting_gem" },
  "overrides" => overrides
}
File.write(File.join(model_directory, "spell_item.json"),
  JSON.pretty_generate(spell_item_model) + "\n")

puts "Generated #{generated.size} spell icons: #{generated.join(', ')}"