-- Various functions to convert tags into something more computable

local ipairs = ipairs
local Way = Way
local string = string

module("tags")

--------------- ACCESS ------------------------------------------------------
--
-- Access is converted into an int with increasing degree of accessibility.
--
--   2 - designated
--   1 - yes
--   0 - unknown
--  -1 - destination
--  -2 - no

local access_values = { ["yes"] = 1, 
                  ["permissive"] = 1, 
                  ["designated"] = 2,
                  ["no"] = -2, 
                  ["private"] = -2, -- should be destination, once it is implemented
                  ["agricultural"] = -2, 
                  ["forestery"] = -2,
                  ["destination"] = -1, 
                  ["delivery"] = -1 
             }

--find first tag in access hierachy which is set
function get_access_tag(taglist, access_tags_hierachy)
    for i,v in ipairs(access_tags_hierachy) do
        local tag = taglist:get_value_by_key(v)
        if tag ~= '' then
            return tag
        end
    end
    return nil
end

-- convert access value into grade
function as_access_grade(value)
    if access_values[value] == nil then
        return 0
    else
        return access_values[value]
    end
end

--find first tag in access hierachy which is set
function get_access_grade(taglist, access_tags_hierachy)
    return as_access_grade(get_access_tag(taglist, access_tags_hierachy))
end

-------------------  SURFACE -----------------------------------------------

function get_surface(taglist)
    local surface = taglist:get_value_by_key('surface')
    if surface ~= '' then
        return surface
    else
        local highway = taglist:get_value_by_key("highway")

        if highway == 'track' then
            local grade = taglist:get_value_by_key("tracktype")
            if grade == "grade1" then
                return "paved"
            elseif grade == "grade3" then
                return "gravel"
            elseif grade == "grade4" then
                return "ground"
            elseif grade == "grade5" then
                return "grass"
            else
                return "unpaved"
            end
        elseif highway == "path" then
            return "ground"
        end
    end
    
    return "paved"
end

------------ NAMING ------------------------------

-- Set the name of the way
function get_name (taglist, name_list)
    for i,v in ipairs(name_list) do
        local tag = taglist:get_value_by_key(v)
        if tag ~= '' then
            return tag
        end
    end
    return ''
end

------------ TRACKS ------------------------------

function get_trackgrade(taglist)
    local grade = taglist:get_value_by_key('tracktype')
    if grade ~= '' then
        s, e, g = string.find(grade, '^grade(%d)$')
        if s == 1 then
            return g
        end
    end

    -- assume grade 2 as default
    return 2
end

------------ ONEWAYS -----------------------------

local oneway_values = {
   ["yes"] = 1,
   ["true"] = 1,
   ["1"] = 1,
   ["no"] = 0,
   ["false"] = 0,
   ["0"] = 0,
   ["opposite"] = -1,
   ["opposite_track"] = -1,
   ["opposite_lane"] = -1,
   ["-1"] = -1
}

function oneway_value(value)
    return oneway_values[value]
end

-- convert to a oneway type (default is bidirectional)
function as_oneway(result, value)
    -- work around the fact that Way may not always
    -- be available at load time
    local ownum = oneway_value(value)
    if ownum == 1 then
        result.forward_mode = 1
        result.backward_mode = 0
    elseif ownum == -1 then
        result.forward_mode = 0
        result.backward_mode = 1
    else
        result.forward_mode = 1
        result.backward_mode = 1
    end
end
