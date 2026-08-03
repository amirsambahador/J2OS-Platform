export default defineAppConfig({
  ui: {
    colors: {
      primary: 'yellow',
      neutral: 'neutral'
    },
    button: {
      compoundVariants: [
        {
          color: 'j2os-warning',
          variant: 'solid',
          class: 'bg-red-600 hover:bg-red-700 text-white'
        },
        {
          color: 'j2os-success',
          variant: 'solid',
          class: 'bg-emerald-600 hover:bg-emerald-700 text-white'
        },
        {
          color: 'j2os-info',
          variant: 'solid',
          class: 'bg-blue-600 hover:bg-blue-700 text-white'
        }
      ]
    },
    pagination: {
      slots: {
        first: 'rotate-180',
        prev: 'rotate-180',
        next: 'rotate-180',
        last: 'rotate-180'
      }
    }
  }
})
