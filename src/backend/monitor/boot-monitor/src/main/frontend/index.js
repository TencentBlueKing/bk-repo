import monitor from './monitor';

// tag::customization-ui-toplevel[]
global.SBA.use({
  install({ viewRegistry, vue, axios }) {
    viewRegistry.addView({
      name: 'monitor',  //<1>
      path: '/monitor', //<2>
      component: monitor, //<3>
      label: '监控', //<4>
      order: 4, //<5>
    });
    vue.prototype.$axios = axios
  }
});
// end::customization-ui-toplevel[]

global.SBA.use({
  install({viewRegistry}) {
    viewRegistry.addView({
      path: '/monitor',
      name: 'about',
      label: 'about.label',
      order: 200,
      component: this,
      isEnabled: () => { return false }
    });
  }
  
});


