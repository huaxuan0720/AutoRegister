package com.billy.android.register

class ScanJarHarvest {

    var harvestList: MutableList<Harvest> = ArrayList<Harvest>()

    class Harvest {
        private var className: String = ""
        private var interfaceName: String? = ""
        private var isInitClass = false

        fun setIsInitClass(isInitClass: Boolean) {
            this.isInitClass = isInitClass
        }

        fun getIsInitClass(): Boolean {
            return isInitClass
        }

        fun getClassName() : String {
            return  className
        }

        fun setClassName(className: String) {
            this.className = className
        }

        fun getInterfaceName() : String? {
            return interfaceName
        }

        fun setInterfaceName(interfaceName: String?) {
            this.interfaceName = interfaceName
        }
    }
}