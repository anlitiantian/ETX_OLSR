local durationIsValid = durationIsValid
local parseDuration = parseDuration
local math = math
local tags = require('tags')
--
-- Function for public transport use in routes
--


module("transport")

local ferry_name_tags = { 'name' }

-- check for ferries and set the parameters accordingly
function is_ferry(way, result, default_speed)
    local route = way:get_value_by_key("route")

    if route == "ferry" then
        local duration = way:get_value_by_key("duration")
        if durationIsValid(duration) then
            result.duration = math.max( parseDuration(duration), 1 );
        else
            result.speed = default_speed
        end
        result.name = tags.get_name(way, ferry_name_tags)
        result.type = 1
        return true
    end
    return false
end
