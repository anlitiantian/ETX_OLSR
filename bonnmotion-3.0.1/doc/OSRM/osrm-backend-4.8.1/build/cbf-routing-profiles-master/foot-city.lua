-- FOOT profile for city walkers - preference for paved underground
--
--
require("tags")
require("barrier")
require("highway")
require("transport")
--
-- Global variables required by extractor
--
ignore_areas 			= true -- future feature
traffic_signal_penalty 	= 2
u_turn_penalty 			= 2
use_turn_restrictions        = false

--
-- Globals for profile definition
--

local access_list = { "foot", "access" }


---------------------------------------------------------------------------
--
-- NODE FUNCTION
--
-- Node    in: lat,lon,id,tags
-- result out: bollard,traffic_light

-- default is forbidden, so add allowed ones only
local barrier_access = {
    ["kerb"] = true,
    ["block"] = true,
    ["bollard"] = true,
    ["border_control"] = true,
    ["cattle_grid"] = true,
    ["entrance"] = true,
    ["sally_port"] = true,
    ["toll_booth"] = true,
    ["cycle_barrier"] = true,
    ["stile"] = true,
    ["block"] = true,
    ["kissing_gate"] = true,
    ["turnstile"] = true,
    ["hampshire_gate"] = true
}

function node_function (node, result)
    barrier.set_bollard(node, result, access_list, barrier_access)

	-- flag delays	
	if result.bollard or node:get_value_by_key("highway") == "traffic_signals" then
		result.traffic_light = true
	end

	return 1
end


---------------------------------------------------------------------------
--
-- WAY FUNCTION
--
-- Way     in: tags
-- result out: String name,
--             double forward_speed,
--             double backward_speed,
--             short type,
--             bool access,
--             bool roundabout,
--             bool is_duration_set,
--             bool is_access_restricted,
--             bool ignore_in_grid,
--             forward_mode { 0, 1, 2 }
--             backward_mode { 0, 1, 2 }
	
--
-- Begin of globals

local default_speed = 10
local designated_speed = 12
local speed_highway = {
    ["footway"] = 12,
	["cycleway"] = 10,
	["primary"] = 7,
	["primary_link"] = 7,
	["secondary"] = 8,
	["secondary_link"] = 8,
	["tertiary"] = 9,
	["tertiary_link"] = 9,
	["residential"] = 10,
	["unclassified"] = 10,
	["living_street"] = 11,
	["road"] = 10,
	["service"] = 10,
	["path"] = 12,
	["pedestrian"] = 12,
	["steps"] = 11,
}

local speed_track = { 11, 11, 10, 9, 9 }

local speed_path = {
    sac_scale = { hiking = 0.5,
                  mountain_hiking = 0,
                  demanding_mountain_hiking = 0,
                  alpine_hiking = 0,
                  demanding_alpine_hiking = 0
                },
    bicycle = { designated = 0.5, yes = 0.9 }
}

local surface_penalties = { 
    ["gravel"] = 0.7,
    ["ground"] = 0.8,
    ["unpaved"] = 0.8,
    ["grass"] = 0.5,
    ["dirt"] = 0.5,
    ["compacted"] = 0.9,
    ["grit"] = 0.8,
    ["sand"] = 0.6
}

local name_list = { "ref", "name" }

function way_function (way, result)
    -- Check if we are allowed to access the way
    if tags.get_access_grade(way, access_list) < -1 then
		return 0
    end

    -- ferries
    if transport.is_ferry(way, result, 5) then
        return 1
    end

    -- is it a valid highway?
    if not highway.set_base_speed(way, result, speed_highway, speed_track) then
        -- check for designated access
        if tags.as_access_grade(way:get_value_by_key('foot')) > 0 then
            result.forward_speed = default_speed
            result.backward_speed = default_speed
        else
            return 0
        end
    end

    if not highway.adjust_speed_for_path(way, result, speed_path) then
        return 0
    end
    if not highway.adjust_speed_by_surface(way, result, surface_penalties, 1.0) then
        return 0
    end

    -- if there is a sidewalk, the better
    local sidewalk = way:get_value_by_key('sidewalk')
    if sidewalk == 'both' or sidewalk == 'left' or sidewalk == 'right' then
        result.forward_speed = default_speed
        result.backward_speed = default_speed
    end

    local junction = way:get_value_by_key('junction')
    if junction == "roundabout" then
        result.roundabout = true
    end
  
    result.name = tags.get_name(way, name_list)
    result.type = 1
    return 1
end
