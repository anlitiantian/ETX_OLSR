-- Functions to filter out ways and adjust speed/weight

local tags = require('tags')
local math = math
local Way = Way
local tonumber = tonumber
local string = string
local ipairs = ipairs
local print = print

module('highway')

local function parse_maxspeed(source)
	if source == nil then
		return 0
	end
	local n = tonumber(source:match("%d*"))
	if n == nil then
		n = 0
	end
	if string.match(source, "mph") or string.match(source, "mp/h") then
		n = (n*1609)/1000;
	end
	return math.abs(n)
end


-- Set basic speed for way
-- Returns false if no speed is defined.
function set_base_speed(source, result, highway_speed, track_speed)
    local highway = source:get_value_by_key('highway')

    if highway == 'track' then
        if track_speed ~= nil then
            local grade = tonumber(tags.get_trackgrade(source))
            if track_speed[grade] ~= nil then
                result.forward_speed = track_speed[grade]
                result.backward_speed = track_speed[grade]
                return true
            end
        end
    else
        if highway_speed[highway] ~= nil then
            result.forward_speed = highway_speed[highway]
            result.backward_speed = highway_speed[highway]
            return true
        end
    end

    result.forward_speed = 0
    result.backward_speed = 0
    return false
end


function adjust_speed_by_surface(source, result, surfaces, default)
    local surface = tags.get_surface(source)

    if surfaces[surface] ~= nil then
        result.forward_speed = math.floor(result.forward_speed * surfaces[surface])
        result.backward_speed = math.floor(result.backward_speed * surfaces[surface])
    else
        result.forward_speed = math.floor(result.forward_speed * default)
        result.backward_speed = math.floor(result.backward_speed * default)
    end

    return result.forward_speed > 0 or result.backward_speed > 0
end

function adjust_speed_for_path(source, result, speeds)
    if source:get_value_by_key("highway") == 'path' then
        for k,v in ipairs(speeds) do
            local tag = source:get_value_by_key(k)
            if tag ~= '' then
                if v == nil then
                    result.forward_speed = 0
                    result.backward_speed = 0
                    return false
                else
                    if v[tag] ~= nil then
                        result.forward_speed = math.floor(result.forward_speed * v[tag])
                        result.backward_speed = math.floor(result.backward_speed * v[tag])
                    end
                end
            end
        end
        return (result.forward_speed > 0 or result.backward_speed > 0)
    end

    return true
end

-- speedfac controls how well the speed limit should be kept
function restrict_to_maxspeed(source, result, speedfac)
	local maxspeed = math.floor(parse_maxspeed(source:get_value_by_key ("maxspeed"))*speedfac)
    if (maxspeed > 0 and maxspeed < result.forward_speed) then
      result.forward_speed = maxspeed
    end
    if (maxspeed > 0 and maxspeed < result.backward_speed) then
      result.backward_speed = maxspeed
    end
    -- check if an explicit speed for backward direction is set
    local maxspeed_forward = parse_maxspeed(source:get_value_by_key("maxspeed:forward"))
    local maxspeed_backward = parse_maxspeed(source:get_value_by_key("maxspeed:backward"))
    if maxspeed_forward > 0 then
        result.forward_speed = maxspeed_forward
    end
    if maxspeed_backward > 0 then
      result.backward_speed = maxspeed_backward
    end
end

function set_directions(source, result, mode)
    local junction = source:get_value_by_key("junction")
    if junction == "roundabout" then
        result.forward_mode = 1
        result.backward_mode = 0
        result.junction = true
    else
        result.junction = false
        if mode ~= nil then
            local onewaymode = source:get_value_by_key(string.format("oneway:%s", mode))
            if onewaymode ~= '' then
                tags.as_oneway(result, onewaymode)
                return true
            end
        end
        local oneway = source:get_value_by_key("oneway")
        if oneway ~= nil then
            tags.as_oneway(result, oneway)
            return true
        end
        local highway = source:get_value_by_key("highway")
        if highway == "motorway" or highway == "motorway_link" then
            result.forward_mode = 1
            result.backward_mode = 0
        end
    end
end

function set_cycleway_directions(way, result)
    set_directions(way, result, "bicycle")
    if (tags.oneway_value(way:get_value_by_key("cycleway")) == -1)
       or (tags.oneway_value(way:get_value_by_key("cycleway:right")) == -1)
       or (tags.oneway_value(way:get_value_by_key("cycleway:left")) == -1) then
         result.backward_mode = 1
    end
end


function turn_function (angle, turn_penalty, turn_bias)
    -- compute turn penalty as angle^2, with a left/right bias
    k = turn_penalty/(90.0*90.0)
	if angle>=0 then
	    return angle*angle*k/turn_bias
	else
	    return angle*angle*k*turn_bias
    end
end
