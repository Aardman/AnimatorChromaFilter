
/**
 * Entry point for filter processing
 * handles API calls from AnimatorFilterPlugin
 */
struct AnimatorFilterController {
    
    private var pipeline: FilterPipeline = FilterPipeline()
    
    //Setup pipeline
    func initialize(){
        pipeline.filterParameters = FilterParameters()
    }
    
    func update(){
        //run in background return result on main thread
    }
    
    func updateFilters(){
        //setting will only update changed parameters
        pipeline.filterParameters = FilterParameters()
    }
    
    func enableFilters(){
        //return filtered data on update
        pipeline.filtersEnabled = true
    }
    
    func disableFilters(){
        //return unfiltered RGB image data on update
        pipeline.filtersEnabled = false
    }
    
}

